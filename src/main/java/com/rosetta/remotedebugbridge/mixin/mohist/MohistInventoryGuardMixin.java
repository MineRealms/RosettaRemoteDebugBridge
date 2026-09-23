package com.rosetta.remotedebugbridge.mixin.mohist;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Guards Mohist's built-in world-management listener against modded items that have no
 * Bukkit {@code Material} mapping. {@code InventoryClickListener.init} calls
 * {@code item.getItemMeta()} unconditionally, which throws
 * {@code IllegalArgumentException: Material cannot be null} and kills the whole
 * {@code ServerboundContainerClickPacket} (the click is silently dropped).
 *
 * The guard runs at HEAD, logs the offending item identity once (class + Forge registry
 * key), then cancels the vanilla handler for that single click. Normal items are untouched.
 *
 * The target class only exists on Mohist, so the config is required=false with
 * defaultRequire=0 and is silently skipped on plain Forge servers.
 */
@Mixin(targets = "com.mohistmc.plugins.world.listener.InventoryClickListener")
public abstract class MohistInventoryGuardMixin {

    private static final Logger ROSETTA_LOGGER = LogManager.getLogger("RosettaNexus/MohistGuard");

    @Inject(method = "init(Lorg/bukkit/event/inventory/InventoryClickEvent;)V", at = @At("HEAD"), cancellable = true, remap = false)
    private static void rosetta$guardNullMaterial(InventoryClickEvent event, CallbackInfo callback) {
        ItemStack item = event.getCurrentItem();
        if (item == null) {
            return;
        }
        try {
            if (item.getType() == null) {
                report(item, "Material==null");
                callback.cancel();
                return;
            }
            item.getItemMeta();
        } catch (Throwable error) {
            report(item, error.toString());
            callback.cancel();
        }
    }

    private static void report(ItemStack item, String reason) {
        StringBuilder sb = new StringBuilder("[MohistGuard] prevented a click crash: reason=").append(reason)
                .append(" bukkitClass=").append(item.getClass().getName());
        try {
            Object handle = item.getClass().getMethod("getHandle").invoke(item);
            Object nmsItem = handle.getClass().getMethod("m_41720_").invoke(handle);
            sb.append(" itemClass=").append(nmsItem.getClass().getName());
            try {
                Object registry = Class.forName("net.minecraftforge.registries.ForgeRegistries").getField("ITEMS").get(null);
                Object key = registry.getClass().getMethod("m_7981_", Object.class).invoke(registry, nmsItem);
                sb.append(" key=").append(key);
            } catch (Throwable ignored) {
            }
        } catch (Throwable ignored) {
        }
        ROSETTA_LOGGER.debug(sb.toString());
    }
}
