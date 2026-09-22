package com.rosetta.remotedebugbridge.debug;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.rosetta.remotedebugbridge.script.util.NetworkUtils;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Server-side CRD sessions (C3/C4): ECDH handshake, encrypted ops, rate limiting, idle timeout.
 * All inbound packets arrive on the server thread; bridge calls block on futures here.
 */
public final class ServerSessions {
    private static final Logger LOGGER = LogManager.getLogger("RosettaRemoteDebugBridge/CRD");
    private static final Gson GSON = new Gson();
    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<UUID, Session>();
    private static final Map<UUID, PendingOpen> PENDING = new ConcurrentHashMap<UUID, PendingOpen>();

    private ServerSessions() {
    }

    private static final class PendingOpen {
        final long sessionId;
        final KeyPair ephemeral;
        final CrdPermission requested;
        final CompletableFuture<JsonObject> future;

        PendingOpen(long sessionId, KeyPair ephemeral, CrdPermission requested, CompletableFuture<JsonObject> future) {
            this.sessionId = sessionId;
            this.ephemeral = ephemeral;
            this.requested = requested;
            this.future = future;
        }
    }

    private static final class Session {
        final UUID playerId;
        final String playerName;
        final long sessionId;
        final byte[] key;
        final CrdPermission permission;
        final String token;
        final int timeoutSeconds;
        final long openedAt;
        volatile long lastActivity;
        final AtomicLong nextServerSeq = new AtomicLong(1);
        final AtomicLong requestIds = new AtomicLong(1);
        final Map<Long, CompletableFuture<JsonObject>> pending = new ConcurrentHashMap<Long, CompletableFuture<JsonObject>>();
        final CrdRateLimiter rate = new CrdRateLimiter(CrdProtocol.MESSAGES_PER_SECOND);
        volatile long lastClientSeq;

        Session(UUID playerId, String playerName, long sessionId, byte[] key, CrdPermission permission,
                String token, int timeoutSeconds) {
            this.playerId = playerId;
            this.playerName = playerName;
            this.sessionId = sessionId;
            this.key = key;
            this.permission = permission;
            this.token = token;
            this.timeoutSeconds = timeoutSeconds;
            this.openedAt = System.currentTimeMillis();
            this.lastActivity = this.openedAt;
        }
    }

    public static JsonObject openSessionBlocking(ServerPlayer player, CrdPermission requested, int timeoutMs) throws Exception {
        UUID id = player.getUUID();
        String name = player.getGameProfile().getName();
        ClientSessionManager.ClientInfo info = ClientSessionManager.find(name);
        if (info == null) {
            throw new IllegalStateException("client has not completed the capability handshake: " + name);
        }
        if (!info.authorized()) {
            throw new IllegalStateException("client remote debug is disabled by its config: " + name);
        }
        if (SESSIONS.containsKey(id)) {
            throw new IllegalStateException("a session is already active for " + name);
        }
        ServerIdentity identity = ServerIdentity.get();
        KeyPair ephemeral = CrdCrypto.generateKeyPair();
        long sessionId = randomLong();
        String ephemeralB64 = CrdCrypto.encodePublicKey(ephemeral.getPublic());
        byte[] signature = CrdCrypto.sign(identity.getKeyPair().getPrivate(), Base64.getDecoder().decode(ephemeralB64));
        CompletableFuture<JsonObject> future = new CompletableFuture<JsonObject>();
        PENDING.put(id, new PendingOpen(sessionId, ephemeral, requested, future));
        CrdSessionRequest request = new CrdSessionRequest(
                sessionId, serverName(player), addressOf(player), identity.getFingerprint(),
                CrdCrypto.encodePublicKey(identity.getKeyPair().getPublic()), ephemeralB64,
                Base64.getEncoder().encodeToString(signature), requested.name(),
                CrdProtocol.SESSION_IDLE_TIMEOUT_SECONDS);
        request.sendToPlayer(player);
        LOGGER.info("[CRD] session request sent to {} (requested={}, fingerprint={})", name, requested, identity.getShortFingerprint());
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        }
        catch (TimeoutException e) {
            PENDING.remove(id);
            throw new IllegalStateException("client did not answer the session request in time");
        }
        catch (ExecutionException e) {
            PENDING.remove(id);
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new IllegalStateException(String.valueOf(cause.getMessage()), cause);
        }
    }

    public static void onAccept(ServerPlayer player, CrdSessionAccept accept) {
        PendingOpen pending = PENDING.remove(player.getUUID());
        String name = player.getGameProfile().getName();
        if (pending == null || pending.sessionId != accept.getSessionId()) {
            LOGGER.warn("[CRD] unexpected session accept from {}", name);
            return;
        }
        try {
            PublicKey clientPublic = CrdCrypto.decodePublicKey(accept.getClientEphemeralKey());
            byte[] secret = CrdCrypto.sharedSecret(pending.ephemeral.getPrivate(), clientPublic);
            byte[] salt = CrdCrypto.sha256(CrdCrypto.concat(pending.ephemeral.getPublic().getEncoded(), clientPublic.getEncoded()));
            byte[] key = CrdCrypto.deriveSessionKey(secret, salt, CrdProtocol.info());
            CrdPermission clientGranted = CrdPermission.parse(accept.getGrantedPermission(), CrdPermission.READ);
            CrdPermission effective = CrdPermission.min(pending.requested, clientGranted);
            int timeout = Math.max(60, Math.min(accept.getTimeoutSeconds(), CrdProtocol.SESSION_IDLE_TIMEOUT_SECONDS));
            String token = CrdCrypto.randomHex(32);
            Session session = new Session(player.getUUID(), name, pending.sessionId, key, effective, token, timeout);
            SESSIONS.put(player.getUUID(), session);

            JsonObject ready = new JsonObject();
            ready.addProperty("token", token);
            ready.addProperty("permission", effective.name());
            ready.addProperty("timeoutSeconds", timeout);
            ready.addProperty("serverName", serverName(player));
            byte[] nonce = CrdCrypto.randomBytes(CrdProtocol.GCM_NONCE_BYTES);
            byte[] sealed = CrdCrypto.encrypt(key, nonce, CrdCrypto.utf8(GSON.toJson(ready)));
            new CrdSessionReady(session.sessionId, nonce, sealed).sendToPlayer(player);

            JsonObject out = new JsonObject();
            out.addProperty("sessionId", session.sessionId);
            out.addProperty("server", serverName(player));
            out.addProperty("client", name);
            out.addProperty("permission", effective.name());
            out.addProperty("requested", pending.requested.name());
            out.addProperty("grantedByClient", clientGranted.name());
            out.addProperty("fingerprint", ServerIdentity.get().getFingerprint());
            out.addProperty("timeoutSeconds", timeout);
            pending.future.complete(out);
            LOGGER.info("[CRD] session opened for {} (permission={}, requested={}, clientGranted={})", name, effective, pending.requested, clientGranted);
        }
        catch (Throwable t) {
            LOGGER.error("[CRD] session accept failed for {}", name, t);
            pending.future.completeExceptionally(t);
        }
    }

    public static void onReject(ServerPlayer player, CrdSessionReject reject) {
        PendingOpen pending = PENDING.remove(player.getUUID());
        String name = player.getGameProfile().getName();
        LOGGER.info("[CRD] client {} rejected session {}: {}", name, reject.getSessionId(), reject.getReason());
        if (pending != null) {
            pending.future.completeExceptionally(new IllegalStateException("client rejected: " + reject.getReason()));
        }
    }

    public static JsonObject opBlocking(ServerPlayer player, String op, JsonObject args, int timeoutMs) throws Exception {
        Session session = requireSession(player);
        if (expireIfIdle(session)) {
            throw new IllegalStateException("session expired by idle timeout");
        }
        CrdPermission required = CrdProtocol.requiredPermission(op);
        if (!session.permission.allows(required)) {
            throw new IllegalStateException("permission " + session.permission + " does not allow '" + op + "' (requires " + required + ")");
        }
        long requestId = session.requestIds.incrementAndGet();
        JsonObject payload = new JsonObject();
        payload.addProperty("requestId", requestId);
        payload.addProperty("op", op);
        payload.add("args", args == null ? new JsonObject() : args);
        payload.addProperty("token", session.token);
        CompletableFuture<JsonObject> future = new CompletableFuture<JsonObject>();
        session.pending.put(requestId, future);
        sendEncrypted(session, player, payload);
        LOGGER.info("[CRD] -> {} op={} request={}", session.playerName, op, requestId);
        try {
            JsonObject response = future.get(timeoutMs, TimeUnit.MILLISECONDS);
            LOGGER.info("[CRD] <- {} op={} ok={}", session.playerName, op, response.has("ok") && response.get("ok").getAsBoolean());
            return response;
        }
        catch (TimeoutException e) {
            session.pending.remove(requestId);
            throw new IllegalStateException("client did not answer op '" + op + "' in time");
        }
        catch (ExecutionException e) {
            session.pending.remove(requestId);
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new IllegalStateException(String.valueOf(cause.getMessage()), cause);
        }
    }

    public static void onClientEnvelope(ServerPlayer player, CrdEnvelope envelope) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || session.sessionId != envelope.getSessionId()) {
            return;
        }
        if (!session.rate.allow()) {
            LOGGER.warn("[CRD] rate limit exceeded by {}; message dropped", session.playerName);
            return;
        }
        if (envelope.getSeq() <= session.lastClientSeq) {
            LOGGER.warn("[CRD] replay/duplicate seq {} from {}; message dropped", envelope.getSeq(), session.playerName);
            return;
        }
        session.lastClientSeq = envelope.getSeq();
        session.lastActivity = System.currentTimeMillis();
        JsonObject payload;
        try {
            payload = GSON.fromJson(CrdCrypto.utf8(CrdCrypto.decrypt(session.key, envelope.getNonce(), envelope.getCiphertext())), JsonObject.class);
        }
        catch (Throwable t) {
            LOGGER.warn("[CRD] decrypt failed for {}: {}", session.playerName, t.toString());
            return;
        }
        if (payload == null || !payload.has("requestId")) {
            return;
        }
        long requestId = payload.get("requestId").getAsLong();
        CompletableFuture<JsonObject> future = session.pending.remove(requestId);
        if (future != null) {
            future.complete(payload);
        }
    }

    public static JsonObject closeSession(ServerPlayer player, String reason) {
        Session session = SESSIONS.remove(player.getUUID());
        if (session == null) {
            JsonObject out = new JsonObject();
            out.addProperty("closed", false);
            out.addProperty("reason", "no active session");
            return out;
        }
        try {
            JsonObject payload = new JsonObject();
            payload.addProperty("op", CrdProtocol.OP_CLOSE);
            payload.addProperty("reason", reason == null ? "closed by server" : reason);
            sendEncrypted(session, player, payload);
        }
        catch (Throwable t) {
            LOGGER.warn("[CRD] failed to notify {} about close: {}", session.playerName, t.toString());
        }
        failPending(session, "session closed");
        LOGGER.info("[CRD] session closed for {} ({})", session.playerName, reason);
        JsonObject out = new JsonObject();
        out.addProperty("closed", true);
        out.addProperty("client", session.playerName);
        out.addProperty("reason", reason == null ? "closed by server" : reason);
        return out;
    }

    public static void onPlayerDisconnect(ServerPlayer player) {
        Session session = SESSIONS.remove(player.getUUID());
        if (session != null) {
            failPending(session, "client disconnected");
            LOGGER.info("[CRD] session removed for {} (disconnect)", session.playerName);
        }
        PENDING.remove(player.getUUID());
    }

    public static JsonObject describe(UUID playerId) {
        Session session = SESSIONS.get(playerId);
        if (session == null) {
            return null;
        }
        JsonObject out = new JsonObject();
        out.addProperty("sessionId", session.sessionId);
        out.addProperty("client", session.playerName);
        out.addProperty("permission", session.permission.name());
        out.addProperty("openedAt", session.openedAt);
        out.addProperty("lastActivity", session.lastActivity);
        out.addProperty("timeoutSeconds", session.timeoutSeconds);
        out.addProperty("pendingRequests", session.pending.size());
        return out;
    }

    private static Session requireSession(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) {
            throw new IllegalStateException("no active CRD session for " + player.getGameProfile().getName()
                    + " (open one with: clientdebug session " + player.getGameProfile().getName() + " open <PERM>)");
        }
        return session;
    }

    private static boolean expireIfIdle(Session session) {
        if (System.currentTimeMillis() - session.lastActivity > session.timeoutSeconds * 1000L) {
            SESSIONS.remove(session.playerId);
            failPending(session, "session expired");
            LOGGER.info("[CRD] session expired for {} after {}s idle", session.playerName, session.timeoutSeconds);
            return true;
        }
        return false;
    }

    private static void failPending(Session session, String reason) {
        for (CompletableFuture<JsonObject> future : session.pending.values()) {
            future.completeExceptionally(new IllegalStateException(reason));
        }
        session.pending.clear();
    }

    private static void sendEncrypted(Session session, ServerPlayer player, JsonObject payload) {
        long seq = session.nextServerSeq.getAndIncrement();
        byte[] nonce = CrdCrypto.randomBytes(CrdProtocol.GCM_NONCE_BYTES);
        byte[] sealed = CrdCrypto.encrypt(session.key, nonce, CrdCrypto.utf8(GSON.toJson(payload)));
        new CrdEnvelope(session.sessionId, seq, nonce, sealed).sendToPlayer(player);
    }

    private static String serverName(ServerPlayer player) {
        return "RosettaNexus @ " + addressOf(player);
    }

    private static String addressOf(ServerPlayer player) {
        try {
            String ip = player.server.getLocalIp();
            return (ip == null || ip.isBlank() ? "*" : ip) + ":" + player.server.getPort();
        }
        catch (Throwable t) {
            return "server";
        }
    }

    private static long randomLong() {
        byte[] bytes = CrdCrypto.randomBytes(8);
        long value = 0L;
        for (byte b : bytes) {
            value = value << 8 | b & 0xFFL;
        }
        return value & Long.MAX_VALUE;
    }
}
