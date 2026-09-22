package com.rosetta.remotedebugbridge.debug;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Server-side registry of clients that completed the C0 capability handshake.
 * Sessions/capabilities beyond the handshake are added in later stages (C1+).
 */
public final class ClientSessionManager {
    private static final Logger LOGGER = LogManager.getLogger("RosettaRemoteDebugBridge/CRD");
    private static final Map<UUID, ClientInfo> CLIENTS = new ConcurrentHashMap<UUID, ClientInfo>();

    private ClientSessionManager() {
    }

    public static void onHello(ServerPlayer player, ClientCapabilityHello hello) {
        ClientInfo info = new ClientInfo(
                player.getUUID(),
                player.getGameProfile().getName(),
                hello.getClientVersion(),
                hello.isAuthorized(),
                hello.getMaxPermission(),
                hello.getModuleVersion(),
                System.currentTimeMillis());
        CLIENTS.put(info.id(), info);
        LOGGER.info("[CRD] capability hello from {} (module v{}, mod={}, authorized={}, maxPermission={})",
                info.name(), info.moduleVersion(), info.clientVersion(), info.authorized(), info.maxPermission());
        if (!info.authorized()) {
            LOGGER.info("[CRD] client {} has remote debug disabled by its config; handshake recorded, no session will be offered", info.name());
        }
        String serverId;
        try {
            String ip = player.server.getLocalIp();
            serverId = (ip == null || ip.isBlank() ? "*" : ip) + ":" + player.server.getPort();
        } catch (Throwable t) {
            serverId = "server";
        }
        ClientCapabilityAck ack = new ClientCapabilityAck(
                true,
                info.authorized(),
                serverId,
                info.authorized()
                        ? "capability registered; remote debug authorized by client config (sessions open in later stages)"
                        : "capability registered; client remote debug disabled by config");
        try {
            ack.sendToPlayer(player);
            LOGGER.info("[CRD] capability ack sent to {} (sessionAllowed={})", info.name(), info.authorized());
        } catch (Throwable t) {
            LOGGER.warn("[CRD] failed to send capability ack to {}: {}", info.name(), t.toString());
        }
    }

    public static void remove(ServerPlayer player) {
        ClientInfo removed = CLIENTS.remove(player.getUUID());
        if (removed != null) {
            LOGGER.info("[CRD] client {} disconnected; handshake entry removed", removed.name());
        }
    }

    public static List<ClientInfo> list() {
        return new ArrayList<ClientInfo>(CLIENTS.values());
    }

    public static ClientInfo find(String name) {
        if (name == null) {
            return null;
        }
        for (ClientInfo info : CLIENTS.values()) {
            if (info.name().equalsIgnoreCase(name)) {
                return info;
            }
        }
        return null;
    }

    public static int count() {
        return CLIENTS.size();
    }

    public record ClientInfo(UUID id, String name, String clientVersion, boolean authorized,
                             String maxPermission, int moduleVersion, long helloAt) {
    }
}
