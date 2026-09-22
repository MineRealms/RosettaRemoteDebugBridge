package com.rosetta.forgekit;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Demonstrates what a HOT-LOADED Bukkit plugin can do with the Forge API on Mohist:
 *
 *  - read Forge registries and the FML mod list (Bukkit cannot see mods)
 *  - listen to runtime game-bus events such as EntityJoinLevelEvent and cancel them,
 *    including for MODDED entities (matched by class name)
 *
 * What cannot be hot-loaded is everything bound to mod construction:
 * RegisterEvent / RegistryEvent / capability registration / @Mod discovery.
 * Those are part of the mod loading window and require a real mod + restart.
 *
 * NOTE: @SubscribeEvent object registration does NOT work from a Bukkit plugin class
 * loader (Forge's ASM event-wrapper class cannot be defined). EventBus 6 lambda
 * listeners with an explicit event type are used instead.
 */
public final class ForgeKitPlugin extends JavaPlugin {

    private final List<Consumer<?>> hooks = new ArrayList<>();
    private volatile String guardToken = null;

    @Override
    public void onEnable() {
        Consumer<EntityJoinLevelEvent> guard = event -> {
            String token = guardToken;
            if (token == null) {
                return;
            }
            String className = event.getEntity().getClass().getName();
            if (className.toLowerCase(Locale.ROOT).contains(token)) {
                event.setCanceled(true);
                getLogger().info("[ForgeKit] blocked EntityJoinLevelEvent: " + className + " -> " + event.getEntity());
            }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, EntityJoinLevelEvent.class, guard);
        hooks.add(guard);
        getLogger().info("[ForgeKit] Forge runtime bus hooked: EntityJoinLevelEvent (guard="
                + (guardToken == null ? "off" : guardToken) + ") [build v1.0.1 hot-update proof]");
    }

    @Override
    public void onDisable() {
        for (Consumer<?> hook : hooks) {
            try {
                MinecraftForge.EVENT_BUS.unregister(hook);
            } catch (Throwable ignored) {
            }
        }
        hooks.clear();
        getLogger().info("[ForgeKit] Forge hooks unregistered (hot unload clean)");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "mods": {
                sender.sendMessage(ChatColor.AQUA + "[ForgeKit] mods loaded: " + ModList.get().getMods().size()
                        + " | registry: items=" + ForgeRegistries.ITEMS.getValues().size()
                        + " blocks=" + ForgeRegistries.BLOCKS.getValues().size()
                        + " biomes=" + ForgeRegistries.BIOMES.getValues().size()
                        + " entityTypes=" + ForgeRegistries.ENTITY_TYPES.getValues().size());
                ModList.get().getMods().stream().limit(20).forEach(mod ->
                        sender.sendMessage(ChatColor.GRAY + "  - " + mod.getModId() + " " + mod.getVersion()));
                return true;
            }
            case "guard": {
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "[ForgeKit] usage: /forgekit guard <class-name-substring|off>");
                    return true;
                }
                if (args[1].equalsIgnoreCase("off")) {
                    guardToken = null;
                    sender.sendMessage(ChatColor.YELLOW + "[ForgeKit] guard off");
                    return true;
                }
                guardToken = args[1].toLowerCase(Locale.ROOT);
                sender.sendMessage(ChatColor.GREEN + "[ForgeKit] guard on: cancelling EntityJoinLevelEvent for entity classes containing '"
                        + guardToken + "' (works for modded entities too)");
                return true;
            }
            case "status":
            default: {
                sender.sendMessage(ChatColor.AQUA + "[ForgeKit] status: guard=" + (guardToken == null ? "off" : guardToken));
                sender.sendMessage(ChatColor.GRAY + "  hot scope  = runtime game bus events (EntityJoinLevelEvent etc.)");
                sender.sendMessage(ChatColor.GRAY + "  NOT hot    = RegisterEvent/RegistryEvent/capabilities (mod construction phase)");
                sender.sendMessage(ChatColor.GRAY + "  usage: /forgekit <mods|guard <class-substring|off>|status>");
                return true;
            }
        }
    }
}
