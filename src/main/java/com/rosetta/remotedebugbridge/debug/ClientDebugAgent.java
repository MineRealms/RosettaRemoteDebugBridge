package com.rosetta.remotedebugbridge.debug;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.rosetta.remotedebugbridge.RosettaRemoteDebugBridge;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.KeyPair;
import java.security.PublicKey;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Client companion module for Client Remote Debug.
 *
 * Side-safety: this class is loaded on both sides (packets reference it), therefore it only
 * touches common APIs. All UI / Minecraft-client work goes through {@link ClientDebugHooks},
 * installed on the client by {@code ClientDebugClientEvents}.
 */
public final class ClientDebugAgent {
    private static final Logger LOGGER = LogManager.getLogger("RosettaRemoteDebugBridge/CRD");
    private static final Gson GSON = new Gson();
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static volatile ClientDebugHooks hooks;
    private static volatile boolean handshakeDone;

    private static volatile long sessionId = -1L;
    private static volatile byte[] sessionKey;
    private static volatile CrdPermission grantedPermission = CrdPermission.READ;
    private static volatile String sessionToken = "";
    private static volatile int sessionTimeoutSeconds = CrdProtocol.SESSION_IDLE_TIMEOUT_SECONDS;
    private static volatile boolean sessionActive;
    private static volatile long lastActivity;
    private static volatile String serverSummary = "";
    private static final AtomicLong NEXT_CLIENT_SEQ = new AtomicLong(1L);
    private static volatile long lastServerSeq;
    private static volatile CrdRateLimiter rate = new CrdRateLimiter(CrdProtocol.MESSAGES_PER_SECOND);

    private ClientDebugAgent() {
    }

    public static void setHooks(ClientDebugHooks clientHooks) {
        hooks = clientHooks;
    }

    public static boolean isSessionActive() {
        return sessionActive;
    }

    public static CrdPermission getGrantedPermission() {
        return grantedPermission;
    }

    public static String getServerSummary() {
        return serverSummary;
    }

    // ------------------------------------------------------------------ C0 lifecycle

    public static void onClientSetup() {
        try {
            ClientDebugConfig config = ClientDebugConfig.get();
            audit("module ready; allowRemoteDebug=" + config.isAllowRemoteDebug()
                    + ", requireConfirmPerSession=" + config.isRequireConfirmPerSession()
                    + ", maxPermission=" + config.getMaxPermission()
                    + ", sessionTimeoutMinutes=" + config.getSessionTimeoutMinutes()
                    + ", configFile=" + config.getFile());
            RosettaRemoteDebugBridge.LOGGER.info("[CRD] client remote debug module ready (allowRemoteDebug={}, maxPermission={})",
                    config.isAllowRemoteDebug(), config.getMaxPermission());
        }
        catch (Throwable t) {
            RosettaRemoteDebugBridge.LOGGER.warn("[CRD] client setup failed: {}", t.toString());
            audit("client setup failed: " + t);
        }
    }

    public static void onLoggingIn() {
        try {
            clearSession("reconnect", false);
            ClientDebugConfig config = ClientDebugConfig.get();
            String version = modVersion();
            ClientCapabilityHello hello = new ClientCapabilityHello(
                    ClientCapabilityHello.MODULE_VERSION,
                    config.isAllowRemoteDebug(),
                    config.getMaxPermission(),
                    version);
            hello.sendToServer();
            handshakeDone = false;
            audit("capability hello sent (moduleVersion=" + ClientCapabilityHello.MODULE_VERSION
                    + ", authorized=" + config.isAllowRemoteDebug()
                    + ", maxPermission=" + config.getMaxPermission()
                    + ", modVersion=" + version + ")");
            RosettaRemoteDebugBridge.LOGGER.info("[CRD] capability hello sent to server (authorized={}, maxPermission={})",
                    config.isAllowRemoteDebug(), config.getMaxPermission());
        }
        catch (Throwable t) {
            RosettaRemoteDebugBridge.LOGGER.warn("[CRD] failed to send capability hello: {}", t.toString());
            audit("capability hello failed: " + t);
        }
    }

    public static void onAck(ClientCapabilityAck ack) {
        handshakeDone = ack.isAccepted();
        audit("capability ack received (accepted=" + ack.isAccepted()
                + ", sessionAllowed=" + ack.isSessionAllowed()
                + ", server=" + ack.getServerId()
                + ", message=" + ack.getMessage() + ")");
        RosettaRemoteDebugBridge.LOGGER.info("[CRD] capability ack received (accepted={}, sessionAllowed={})",
                ack.isAccepted(), ack.isSessionAllowed());
    }

    public static void onLoggingOut() {
        if (sessionActive) {
            audit("disconnected from server; session cleared");
            clearSession("disconnected", true);
        }
        handshakeDone = false;
    }

    public static boolean isHandshakeDone() {
        return handshakeDone;
    }

    // ------------------------------------------------------------------ C3 session handshake

    public static void onSessionRequest(CrdSessionRequest request) {
        String server = request.getServerName() + " [" + shortFingerprint(request.getFingerprint()) + "]";
        try {
            ClientDebugConfig config = ClientDebugConfig.get();
            if (!config.isAllowRemoteDebug()) {
                reject(request, "remote debug disabled by client config");
                return;
            }
            if (sessionActive) {
                reject(request, "a session is already active");
                return;
            }
            ClientDebugHooks currentHooks = hooks;
            String address = currentHooks == null ? "unknown" : String.valueOf(currentHooks.remoteAddress());
            ClientTrustStore.Trust trust = ClientTrustStore.check(address, request.getFingerprint());
            if (trust == ClientTrustStore.Trust.MISMATCH) {
                audit("session rejected: fingerprint changed for " + address + " (possible MITM)");
                notify(false, "CRD: server fingerprint changed, session rejected");
                reject(request, "server fingerprint changed; session rejected");
                return;
            }
            PublicKey identity;
            try {
                identity = CrdCrypto.decodePublicKey(request.getIdentityKey());
            }
            catch (Throwable t) {
                reject(request, "server identity key is invalid");
                return;
            }
            if (!CrdCrypto.fingerprint(identity).equalsIgnoreCase(request.getFingerprint())) {
                reject(request, "server identity fingerprint mismatch");
                return;
            }
            byte[] signedData = Base64.getDecoder().decode(request.getEphemeralKey());
            if (!CrdCrypto.verify(identity, signedData, Base64.getDecoder().decode(request.getSignature()))) {
                reject(request, "server identity signature verification failed");
                return;
            }
            CrdPermission requested = CrdPermission.parse(request.getRequestedPermission(), CrdPermission.READ);
            CrdPermission grant = CrdPermission.min(requested, config.getMaxPermissionValue());
            Consumer<Boolean> decision = accepted -> {
                if (accepted) {
                    acceptSession(request, grant, trust == ClientTrustStore.Trust.NEW ? address : null, config);
                }
                else {
                    reject(request, "declined by player");
                }
            };
            if (config.isRequireConfirmPerSession() && currentHooks != null) {
                audit("session confirm requested: " + server + " permission=" + grant + " timeout=" + request.getTimeoutSeconds() + "s");
                currentHooks.requestSessionConfirm(request, grant, decision);
            }
            else {
                decision.accept(true);
            }
        }
        catch (Throwable t) {
            LOGGER.warn("[CRD] session request handling failed: {}", t.toString());
            audit("session request failed: " + t);
            reject(request, "client error: " + t);
        }
    }

    private static void acceptSession(CrdSessionRequest request, CrdPermission grant, String rememberAddress, ClientDebugConfig config) {
        try {
            KeyPair clientKeyPair = CrdCrypto.generateKeyPair();
            PublicKey serverEphemeral = CrdCrypto.decodePublicKey(request.getEphemeralKey());
            byte[] secret = CrdCrypto.sharedSecret(clientKeyPair.getPrivate(), serverEphemeral);
            byte[] salt = CrdCrypto.sha256(CrdCrypto.concat(serverEphemeral.getEncoded(), clientKeyPair.getPublic().getEncoded()));
            sessionKey = CrdCrypto.deriveSessionKey(secret, salt, CrdProtocol.info());
            sessionId = request.getSessionId();
            grantedPermission = grant;
            serverSummary = request.getServerName();
            if (rememberAddress != null) {
                ClientTrustStore.remember(rememberAddress, request.getFingerprint());
                audit("TOFU pinned " + rememberAddress + " -> " + shortFingerprint(request.getFingerprint()));
            }
            int timeout = Math.max(60, config.getSessionTimeoutMinutes() * 60);
            new CrdSessionAccept(sessionId, CrdCrypto.encodePublicKey(clientKeyPair.getPublic()),
                    CrdCrypto.randomHex(16), grant.name(), timeout).sendToServer();
            audit("session accept sent (session=" + sessionId + ", permission=" + grant + ", timeout=" + timeout + "s)");
            RosettaRemoteDebugBridge.LOGGER.info("[CRD] session accepted (permission={}, timeout={}s)", grant, timeout);
        }
        catch (Throwable t) {
            LOGGER.warn("[CRD] failed to accept session: {}", t.toString());
            audit("session accept failed: " + t);
            clearSession("accept failed", false);
        }
    }

    public static void onSessionReady(CrdSessionReady ready) {
        if (sessionKey == null || ready.getSessionId() != sessionId) {
            return;
        }
        try {
            JsonObject payload = GSON.fromJson(
                    CrdCrypto.utf8(CrdCrypto.decrypt(sessionKey, ready.getNonce(), ready.getCiphertext())),
                    JsonObject.class);
            if (payload == null || !payload.has("token")) {
                clearSession("bad session ready", false);
                return;
            }
            sessionToken = payload.get("token").getAsString();
            if (payload.has("permission")) {
                grantedPermission = CrdPermission.parse(payload.get("permission").getAsString(), grantedPermission);
            }
            if (payload.has("timeoutSeconds")) {
                sessionTimeoutSeconds = Math.max(60, payload.get("timeoutSeconds").getAsInt());
            }
            sessionActive = true;
            lastActivity = System.currentTimeMillis();
            NEXT_CLIENT_SEQ.set(1L);
            lastServerSeq = 0L;
            rate = new CrdRateLimiter(CrdProtocol.MESSAGES_PER_SECOND);
            audit("session ready: " + serverSummary + " permission=" + grantedPermission + " timeout=" + sessionTimeoutSeconds + "s");
            RosettaRemoteDebugBridge.LOGGER.info("[CRD] session active with {} (permission={})", serverSummary, grantedPermission);
            notify(true, "CRD session active: " + serverSummary + " (" + grantedPermission + ")");
        }
        catch (Throwable t) {
            LOGGER.warn("[CRD] failed to open session: {}", t.toString());
            audit("session ready failed: " + t);
            clearSession("session ready failed", false);
        }
    }

    // ------------------------------------------------------------------ C1/C2/C4 encrypted ops

    public static void onEnvelope(CrdEnvelope envelope) {
        if (!sessionActive || envelope.getSessionId() != sessionId) {
            return;
        }
        if (!rate.allow()) {
            audit("rate limit exceeded; inbound message dropped");
            return;
        }
        if (envelope.getSeq() <= lastServerSeq) {
            audit("replay/duplicate seq " + envelope.getSeq() + " dropped");
            return;
        }
        lastServerSeq = envelope.getSeq();
        lastActivity = System.currentTimeMillis();
        JsonObject payload;
        try {
            payload = GSON.fromJson(
                    CrdCrypto.utf8(CrdCrypto.decrypt(sessionKey, envelope.getNonce(), envelope.getCiphertext())),
                    JsonObject.class);
        }
        catch (Throwable t) {
            audit("decrypt failed: " + t);
            return;
        }
        if (payload == null) {
            return;
        }
        if (payload.has("op") && CrdProtocol.OP_CLOSE.equals(payload.get("op").getAsString())) {
            String reason = payload.has("reason") ? payload.get("reason").getAsString() : "closed by server";
            audit("server closed session: " + reason);
            clearSession(reason, true);
            return;
        }
        if (!payload.has("requestId") || !payload.has("op")) {
            return;
        }
        long requestId = payload.get("requestId").getAsLong();
        String op = payload.get("op").getAsString();
        JsonObject args = payload.has("args") && payload.get("args").isJsonObject()
                ? payload.getAsJsonObject("args") : new JsonObject();
        if (payload.has("token") && !sessionToken.isEmpty()
                && !sessionToken.equals(payload.get("token").getAsString())) {
            audit("token mismatch for op " + op + "; denied");
            sendResponse(requestId, false, null, "invalid session token");
            return;
        }
        CrdPermission required = CrdProtocol.requiredPermission(op);
        if (!grantedPermission.allows(required)) {
            audit("op " + op + " denied (requires " + required + ", granted " + grantedPermission + ")");
            sendResponse(requestId, false, null, "permission denied: " + op + " requires " + required);
            return;
        }
        ClientDebugHooks currentHooks = hooks;
        if (currentHooks == null) {
            sendResponse(requestId, false, null, "client hooks unavailable (not a client runtime?)");
            return;
        }
        audit("op received: " + op);
        currentHooks.execute(op, args, result -> {
            if (result != null && result.has("ok") && result.get("ok").getAsBoolean()) {
                sendResponse(requestId, true, result.has("result") ? result.get("result") : new JsonObject(), null);
            }
            else {
                String error = result != null && result.has("error") ? result.get("error").getAsString() : "unknown client error";
                sendResponse(requestId, false, null, error);
            }
        });
    }

    private static void sendResponse(long requestId, boolean ok, com.google.gson.JsonElement result, String error) {
        if (!sessionActive || sessionKey == null) {
            audit("response for request " + requestId + " dropped (session already closed)");
            return;
        }
        JsonObject payload = new JsonObject();
        payload.addProperty("requestId", requestId);
        payload.addProperty("ok", ok);
        if (ok && result != null) {
            payload.add("result", result);
        }
        if (!ok) {
            payload.addProperty("error", error == null ? "error" : error);
        }
        try {
            sendEncrypted(payload);
        }
        catch (Throwable t) {
            audit("failed to send response " + requestId + ": " + t);
        }
    }

    private static void sendEncrypted(JsonObject payload) {
        long seq = NEXT_CLIENT_SEQ.getAndIncrement();
        byte[] nonce = CrdCrypto.randomBytes(CrdProtocol.GCM_NONCE_BYTES);
        byte[] sealed = CrdCrypto.encrypt(sessionKey, nonce, CrdCrypto.utf8(GSON.toJson(payload)));
        new CrdEnvelope(sessionId, seq, nonce, sealed).sendToServer();
    }

    // ------------------------------------------------------------------ disconnects

    /** `/crd disconnect` and idle expiry. */
    public static void disconnect(String reason) {
        if (sessionActive) {
            try {
                JsonObject payload = new JsonObject();
                payload.addProperty("op", CrdProtocol.OP_CLOSE);
                payload.addProperty("reason", reason);
                sendEncrypted(payload);
            }
            catch (Throwable ignored) {
            }
            audit("disconnected: " + reason);
        }
        clearSession(reason, true);
    }

    public static void tick() {
        if (sessionActive && System.currentTimeMillis() - lastActivity > sessionTimeoutSeconds * 1000L) {
            disconnect("idle timeout");
        }
    }

    private static void clearSession(String reason, boolean notifyPlayer) {
        boolean wasActive = sessionActive;
        sessionActive = false;
        sessionKey = null;
        sessionId = -1L;
        sessionToken = "";
        NEXT_CLIENT_SEQ.set(1L);
        lastServerSeq = 0L;
        grantedPermission = CrdPermission.READ;
        if (wasActive && notifyPlayer) {
            notify(false, "CRD session ended: " + reason);
        }
    }

    private static void reject(CrdSessionRequest request, String reason) {
        try {
            new CrdSessionReject(request.getSessionId(), reason).sendToServer();
        }
        catch (Throwable t) {
            LOGGER.warn("[CRD] failed to send session reject: {}", t.toString());
        }
        audit("session rejected: " + reason + " (server=" + request.getServerName() + ")");
        RosettaRemoteDebugBridge.LOGGER.info("[CRD] session rejected: {}", reason);
    }

    private static void notify(boolean active, String message) {
        ClientDebugHooks currentHooks = hooks;
        if (currentHooks != null) {
            try {
                currentHooks.notifySession(active, message);
            }
            catch (Throwable ignored) {
            }
        }
    }

    private static String shortFingerprint(String fingerprint) {
        if (fingerprint == null || fingerprint.isEmpty()) {
            return "unknown";
        }
        return fingerprint.length() > 16 ? fingerprint.substring(0, 16) : fingerprint;
    }

    private static String modVersion() {
        try {
            return ModList.get().getModContainerById(RosettaRemoteDebugBridge.MOD_ID)
                    .map(container -> container.getModInfo().getVersion().toString())
                    .orElse("unknown");
        }
        catch (Throwable t) {
            return "unknown";
        }
    }

    private static void audit(String line) {
        try {
            Path dir = FMLPaths.GAMEDIR.get().resolve("logs").resolve("Rosetta");
            Files.createDirectories(dir);
            String entry = "[" + LocalDateTime.now().format(TS) + "] " + line + System.lineSeparator();
            Files.writeString(dir.resolve("client-debug.log"), entry, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
        catch (Throwable ignored) {
        }
    }
}
