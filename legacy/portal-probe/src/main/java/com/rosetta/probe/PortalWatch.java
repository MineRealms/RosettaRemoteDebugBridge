package com.rosetta.probe;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Watches online players for "standing in a nether portal" sessions and reports:
 * - entering / leaving a portal
 * - periodic read-only destination prechecks
 * - SILENT FAILURE when a player stands in a portal long enough but no dimension change happened
 * - TRAVEL COMPLETED when the world change finally fires
 */
final class PortalWatch {

    private static final class Session {
        long portalTicks;
        long lastPrecheckAt;
        boolean silentLogged;
        boolean portalEventSeen;
        boolean portalEventCancelled;
        boolean portalEventToNull;
        boolean teleportEventSeen;
        boolean travelDone;
        String lastPrecheck;
        String lastEventDetail;
    }

    private final Plugin plugin;
    private final ProbeLog log;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final int intervalTicks;
    private final int silentTicks;
    private final int precheckAfterTicks;
    private final long precheckEveryMs;
    private BukkitTask task;

    PortalWatch(Plugin plugin, ProbeLog log, int intervalTicks, int silentTicks, int precheckAfterTicks, int precheckEveryTicks) {
        this.plugin = plugin;
        this.log = log;
        this.intervalTicks = Math.max(1, intervalTicks);
        this.silentTicks = Math.max(20, silentTicks);
        this.precheckAfterTicks = Math.max(1, precheckAfterTicks);
        this.precheckEveryMs = Math.max(1, precheckEveryTicks) * 50L;
    }

    void start() {
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::scan, intervalTicks, intervalTicks);
    }

    void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        sessions.clear();
    }

    int trackedPlayers() {
        return sessions.size();
    }

    private void scan() {
        try {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                boolean inPortal = PortalDiagnostics.inPortalBlock(player)
                        || "true".equals(PortalDiagnostics.insidePortal(player));
                Session session = sessions.get(player.getUniqueId());
                if (inPortal) {
                    if (session == null) {
                        session = new Session();
                        sessions.put(player.getUniqueId(), session);
                        log.info("[Watch] " + player.getName() + " entered a portal: " + PortalDiagnostics.snapshot(player));
                    }
                    session.portalTicks += intervalTicks;
                    maybePrecheck(player, session);
                    maybeSilentFailure(player, session);
                } else if (session != null) {
                    endSession(player, session);
                }
            }
            sessions.entrySet().removeIf(entry -> plugin.getServer().getPlayer(entry.getKey()) == null);
        } catch (Throwable error) {
            log.warn("[Watch] scan error: " + error);
        }
    }

    private void maybePrecheck(Player player, Session session) {
        long now = System.currentTimeMillis();
        if (session.portalTicks < precheckAfterTicks || now - session.lastPrecheckAt < precheckEveryMs) {
            return;
        }
        session.lastPrecheckAt = now;
        session.lastPrecheck = PortalDiagnostics.precheck(player);
        log.info("[Watch] " + player.getName() + " in portal " + seconds(session.portalTicks) + "s -> " + session.lastPrecheck);
    }

    private void maybeSilentFailure(Player player, Session session) {
        if (session.silentLogged || session.travelDone || session.portalTicks < silentTicks) {
            return;
        }
        if (session.portalEventSeen && !session.portalEventCancelled && !session.portalEventToNull) {
            return;
        }
        session.silentLogged = true;
        StringBuilder message = new StringBuilder();
        message.append("SILENT PORTAL FAILURE player=").append(player.getName())
                .append(" stood ").append(seconds(session.portalTicks)).append("s in a NETHER_PORTAL without traveling");
        message.append("\n  state: ").append(PortalDiagnostics.snapshot(player));
        message.append("\n  portalEventSeen=").append(session.portalEventSeen)
                .append(" cancelled=").append(session.portalEventCancelled)
                .append(" toNull=").append(session.portalEventToNull)
                .append(" teleportEventSeen=").append(session.teleportEventSeen);
        if (session.lastEventDetail != null) {
            message.append("\n  lastEvent: ").append(session.lastEventDetail);
        }
        if (session.lastPrecheck != null) {
            message.append("\n  ").append(session.lastPrecheck);
        }
        message.append("\n  ").append(PortalDiagnostics.silentFailureHint(session.portalEventSeen, session.portalEventCancelled));
        log.warn(message.toString());
    }

    private void endSession(Player player, Session session) {
        sessions.remove(player.getUniqueId());
        if (session.portalTicks >= Math.max(40, intervalTicks) && !session.travelDone) {
            log.info("[Watch] " + player.getName() + " left the portal after " + seconds(session.portalTicks)
                    + "s without a completed dimension change. portalEventSeen=" + session.portalEventSeen
                    + " cancelled=" + session.portalEventCancelled
                    + " precheck=" + (session.lastPrecheck == null ? "n/a" : session.lastPrecheck));
        }
    }

    void onPortalEvent(Player player, boolean cancelled, boolean toNull, String detail) {
        Session session = sessions.computeIfAbsent(player.getUniqueId(), id -> new Session());
        session.portalEventSeen = true;
        session.portalEventCancelled |= cancelled;
        session.portalEventToNull |= toNull;
        session.lastEventDetail = "PlayerPortalEvent " + detail;
        if (cancelled || toNull) {
            log.warn("PlayerPortalEvent BLOCKED for " + player.getName()
                    + " (" + (cancelled ? "cancelled" : "to=null") + "): " + detail);
        } else {
            log.info("[Watch] PlayerPortalEvent accepted for " + player.getName() + ": " + detail);
        }
    }

    void onTeleportEvent(Player player) {
        Session session = sessions.get(player.getUniqueId());
        if (session != null) {
            session.teleportEventSeen = true;
        }
    }

    void onChangedWorld(Player player, String fromWorld) {
        Session session = sessions.remove(player.getUniqueId());
        if (session != null && session.portalTicks >= Math.max(20, intervalTicks)) {
            session.travelDone = true;
            log.info("[Watch] TRAVEL COMPLETED player=" + player.getName()
                    + " from=" + fromWorld + " to=" + player.getWorld().getName()
                    + " portalTime=" + seconds(session.portalTicks) + "s");
        }
    }

    void trace(Player player, int seconds) {
        final int maxTicks = Math.max(1, seconds) * 20;
        final int[] ticks = {0};
        final BukkitTask[] holder = new BukkitTask[1];
        holder[0] = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            ticks[0]++;
            log.info("[Trace] " + player.getName() + " " + PortalDiagnostics.snapshot(player));
            if (ticks[0] >= maxTicks || !player.isOnline()) {
                log.info("[Trace] finished for " + player.getName() + " (" + ticks[0] + " ticks)");
                holder[0].cancel();
            }
        }, 1L, 1L);
    }

    private static String seconds(long ticks) {
        return String.format("%.1f", ticks / 20.0D);
    }
}
