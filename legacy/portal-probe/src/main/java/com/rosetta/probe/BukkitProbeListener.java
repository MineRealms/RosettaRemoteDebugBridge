package com.rosetta.probe;

import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.PortalCreateEvent;

final class BukkitProbeListener implements Listener {

    private final ProbeLog log;
    private final PortalWatch watch;
    private final boolean logAllTeleports;

    BukkitProbeListener(ProbeLog log, PortalWatch watch, boolean logAllTeleports) {
        this.log = log;
        this.watch = watch;
        this.logAllTeleports = logAllTeleports;
    }

    // ---------------------------------------------------------------- PlayerPortalEvent

    @EventHandler(priority = EventPriority.LOWEST)
    public void portalLowest(PlayerPortalEvent event) {
        log.info("[LOWEST ] " + portalDetail(event));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void portalMonitor(PlayerPortalEvent event) {
        log.info("[MONITOR] " + portalDetail(event));
        boolean toNull = event.getTo() == null;
        if (event.isCancelled()) {
            log.warn("PlayerPortalEvent CANCELLED by a plugin for " + event.getPlayer().getName()
                    + " -> travel aborts silently. Run '/portalprobe listeners' and check every plugin"
                    + " with priority != MONITOR.");
        } else if (toNull) {
            log.warn("PlayerPortalEvent to=null for " + event.getPlayer().getName()
                    + " -> travel aborts silently.");
        }
        watch.onPortalEvent(event.getPlayer(), event.isCancelled(), toNull, portalDetail(event));
    }

    private static String portalDetail(PlayerPortalEvent event) {
        return String.format("PlayerPortalEvent player=%s cause=%s cancelled=%s from=%s to=%s"
                        + " searchRadius=%s canCreatePortal=%s creationRadius=%s",
                event.getPlayer().getName(), event.getCause(), event.isCancelled(),
                loc(event.getFrom()), loc(event.getTo()),
                getter(event, "getSearchRadius"), getter(event, "getCanCreatePortal"), getter(event, "getCreationRadius"));
    }

    // ---------------------------------------------------------------- PlayerTeleportEvent

    @EventHandler(priority = EventPriority.LOWEST)
    public void teleportLowest(PlayerTeleportEvent event) {
        if (!logAllTeleports && !isPortalCause(event.getCause())) {
            return;
        }
        log.info("[LOWEST ] " + teleportDetail(event));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void teleportMonitor(PlayerTeleportEvent event) {
        if (!logAllTeleports && !isPortalCause(event.getCause())) {
            return;
        }
        log.info("[MONITOR] " + teleportDetail(event));
        if (event.isCancelled()) {
            log.warn("PlayerTeleportEvent CANCELLED by a plugin for " + event.getPlayer().getName()
                    + " cause=" + event.getCause() + " -> dimension change aborts silently.");
        } else if (event.getTo() == null) {
            log.warn("PlayerTeleportEvent to=null for " + event.getPlayer().getName()
                    + " cause=" + event.getCause() + " -> dimension change aborts silently.");
        }
        watch.onTeleportEvent(event.getPlayer());
    }

    private static String teleportDetail(PlayerTeleportEvent event) {
        return String.format("PlayerTeleportEvent player=%s cause=%s cancelled=%s from=%s to=%s",
                event.getPlayer().getName(), event.getCause(), event.isCancelled(),
                loc(event.getFrom()), loc(event.getTo()));
    }

    // ---------------------------------------------------------------- completion / portal creation

    @EventHandler(priority = EventPriority.MONITOR)
    public void changedWorld(PlayerChangedWorldEvent event) {
        String from = event.getFrom() == null ? "?" : event.getFrom().getName();
        log.info("[Watch] PlayerChangedWorldEvent player=" + event.getPlayer().getName()
                + " from=" + from + " to=" + event.getPlayer().getWorld().getName());
        watch.onChangedWorld(event.getPlayer(), from);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void portalCreate(PortalCreateEvent event) {
        log.info("[Watch] PortalCreateEvent world=" + event.getWorld().getName()
                + " reason=" + event.getReason()
                + " entity=" + (event.getEntity() == null ? "none" : event.getEntity().getType())
                + " blocks=" + event.getBlocks().size()
                + " cancelled=" + event.isCancelled()
                + (!event.isCancelled() ? "" : " <- a plugin blocked the exit-portal creation"));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void entityPortal(EntityPortalEvent event) {
        if (event.getEntity() instanceof org.bukkit.entity.Player) {
            return;
        }
        log.info(String.format("EntityPortalEvent entity=%s type=%s cancelled=%s from=%s to=%s",
                event.getEntity().getUniqueId(), event.getEntity().getType(), event.isCancelled(),
                loc(event.getFrom()), loc(event.getTo())));
    }

    // ---------------------------------------------------------------- helpers

    private static Object getter(PlayerPortalEvent event, String name) {
        try {
            return PlayerPortalEvent.class.getMethod(name).invoke(event);
        } catch (Throwable ignored) {
            return "n/a";
        }
    }

    private static boolean isPortalCause(PlayerTeleportEvent.TeleportCause cause) {
        return cause == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL
                || cause == PlayerTeleportEvent.TeleportCause.END_PORTAL;
    }

    private static String loc(Location location) {
        if (location == null) {
            return "null";
        }
        return String.format("%s[%.1f,%.1f,%.1f]",
                location.getWorld() == null ? "?" : location.getWorld().getName(),
                location.getX(), location.getY(), location.getZ());
    }
}
