package com.rosetta.probe;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent;
import net.minecraftforge.eventbus.api.EventPriority;

/**
 * Forge side of the probe: a Bukkit plugin on Mohist can use the Forge API directly.
 *
 * NOTE: @SubscribeEvent object registration does NOT work from a Bukkit plugin class loader:
 * Forge's EventBus generates an ASM wrapper class and fails to define it
 * (ClassNotFoundException {@code __ForgeProbeListener_onTravelLowest_...}). EventBus 6 lambda
 * listeners with an explicit event type avoid the generated wrapper.
 */
public final class ForgeProbeListener {

    private final ProbeLog log;
    private final List<Consumer<?>> listeners = new ArrayList<>();

    public ForgeProbeListener(ProbeLog log) {
        this.log = log;
        Consumer<EntityTravelToDimensionEvent> lowest = event ->
                log.info("[Forge/LOWEST ] EntityTravelToDimensionEvent cancelled=" + event.isCanceled() + describe(event));
        Consumer<EntityTravelToDimensionEvent> monitor = event -> {
            log.info("[Forge/MONITOR] EntityTravelToDimensionEvent cancelled=" + event.isCanceled() + describe(event));
            if (event.isCanceled()) {
                log.warn("EntityTravelToDimensionEvent CANCELLED by a mod/Forge hook -> ServerPlayer.changeDimension()"
                        + " returns null silently." + describe(event));
            }
        };
        add(EventPriority.LOWEST, EntityTravelToDimensionEvent.class, lowest, false);
        add(EventPriority.MONITOR, EntityTravelToDimensionEvent.class, monitor, true);
        try {
            Consumer<PlayerChangedDimensionEvent> changed = event ->
                    log.info("[Forge] PlayerChangedDimensionEvent player=" + PortalDiagnostics.describeEntity(event.getEntity())
                            + " from=" + event.getFrom() + " to=" + event.getTo());
            add(EventPriority.MONITOR, PlayerChangedDimensionEvent.class, changed, false);
            log.info("[PortalProbe] Forge hooks: EntityTravelToDimensionEvent(LOWEST/MONITOR) + PlayerChangedDimensionEvent");
        } catch (Throwable error) {
            log.info("[PortalProbe] Forge hooks: EntityTravelToDimensionEvent(LOWEST/MONITOR);"
                    + " PlayerChangedDimensionEvent unavailable: " + error);
        }
    }

    private <T extends net.minecraftforge.eventbus.api.Event> void add(EventPriority priority, Class<T> type,
                                                                       Consumer<T> consumer, boolean receiveCancelled) {
        MinecraftForge.EVENT_BUS.addListener(priority, receiveCancelled, type, consumer);
        listeners.add(consumer);
    }

    public void unregister() {
        for (Consumer<?> consumer : listeners) {
            try {
                MinecraftForge.EVENT_BUS.unregister(consumer);
            } catch (Throwable ignored) {
            }
        }
        listeners.clear();
    }

    private static String describe(EntityTravelToDimensionEvent event) {
        return " entity=" + PortalDiagnostics.describeEntity(event.getEntity()) + " to=" + event.getDimension();
    }
}
