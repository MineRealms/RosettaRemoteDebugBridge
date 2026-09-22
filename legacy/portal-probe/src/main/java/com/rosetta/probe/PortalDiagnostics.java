package com.rosetta.probe;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.RegisteredListener;

/**
 * All NMS access happens here and is fully reflective, so the plugin works on both a
 * dev (MCP) and a production (SRG) Mohist server. Candidate names are tried in order.
 */
final class PortalDiagnostics {

    private PortalDiagnostics() {
    }

    // ---------------------------------------------------------------- reflection

    static Object nms(Object target, String... names) {
        if (target == null) {
            return null;
        }
        for (Class<?> type = target.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (String name : names) {
                try {
                    Method method = type.getDeclaredMethod(name);
                    if (method.getParameterCount() == 0) {
                        method.setAccessible(true);
                        return method.invoke(target);
                    }
                } catch (NoSuchMethodException ignored) {
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }

    static Object invoke(Object target, String name, Object... args) {
        if (target == null) {
            return null;
        }
        for (Class<?> type = target.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                if (!method.getName().equals(name) || method.getParameterCount() != args.length) {
                    continue;
                }
                if (!assignable(method.getParameterTypes(), args)) {
                    continue;
                }
                try {
                    method.setAccessible(true);
                    return method.invoke(target, args);
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }

    static Object readField(Object target, String... names) {
        if (target == null) {
            return null;
        }
        for (Class<?> type = target.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (String name : names) {
                try {
                    Field field = type.getDeclaredField(name);
                    field.setAccessible(true);
                    return field.get(target);
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }

    static Object noArgReturning(Object target, String returnTypeSubstring) {
        if (target == null) {
            return null;
        }
        for (Class<?> type = target.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                if (method.getParameterCount() != 0 || method.getReturnType().isPrimitive()) {
                    continue;
                }
                if (method.getReturnType().getName().contains(returnTypeSubstring)) {
                    try {
                        method.setAccessible(true);
                        return method.invoke(target);
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
        return null;
    }

    private static boolean assignable(Class<?>[] types, Object[] args) {
        for (int i = 0; i < types.length; i++) {
            Class<?> type = types[i];
            Object arg = args[i];
            if (arg == null) {
                if (type.isPrimitive()) {
                    return false;
                }
                continue;
            }
            if (type == int.class || type == Integer.class) {
                if (!(arg instanceof Integer)) {
                    return false;
                }
            } else if (type == long.class || type == Long.class) {
                if (!(arg instanceof Long)) {
                    return false;
                }
            } else if (type == boolean.class || type == Boolean.class) {
                if (!(arg instanceof Boolean)) {
                    return false;
                }
            } else if (type == double.class || type == Double.class) {
                if (!(arg instanceof Double)) {
                    return false;
                }
            } else if (!type.isInstance(arg)) {
                return false;
            }
        }
        return true;
    }

    // ---------------------------------------------------------------- portal state

    static Object nmsPlayer(Player player) {
        return nms(player, "getHandle");
    }

    static String insidePortal(Player player) {
        Object value = readField(nmsPlayer(player), "f_19817_", "isInsidePortal");
        return value == null ? "unknown" : String.valueOf(value);
    }

    static boolean inPortalBlock(Player player) {
        try {
            if (player.getLocation().getBlock().getType() == Material.NETHER_PORTAL) {
                return true;
            }
            return player.getEyeLocation().getBlock().getType() == Material.NETHER_PORTAL;
        } catch (Throwable ignored) {
            return false;
        }
    }

    static String snapshot(Player player) {
        StringBuilder sb = new StringBuilder();
        sb.append("world=").append(player.getWorld().getName())
                .append(" pos=[").append(fmt(player.getLocation().getX())).append(',')
                .append(fmt(player.getLocation().getY())).append(',')
                .append(fmt(player.getLocation().getZ())).append(']')
                .append(" feet=").append(player.getLocation().getBlock().getType())
                .append(" eye=").append(player.getEyeLocation().getBlock().getType())
                .append(" insidePortal=").append(insidePortal(player))
                .append(" portalCooldown=").append(player.getPortalCooldown())
                .append(" vehicle=").append(player.getVehicle() == null ? "none" : player.getVehicle().getType())
                .append(" passenger=").append(!player.getPassengers().isEmpty())
                .append(" gameMode=").append(player.getGameMode())
                .append(" dead=").append(player.isDead());
        return sb.toString();
    }

    // ---------------------------------------------------------------- levels

    static Object nmsWorld(World world) {
        return nms(world, "getHandle");
    }

    static String levelKey(Object nmsLevel) {
        Object key = nms(nmsLevel, "m_46472_", "dimension");
        return key == null ? "unknown" : String.valueOf(key);
    }

    static Object findLevel(boolean nether) {
        for (World world : Bukkit.getWorlds()) {
            Object level = nmsWorld(world);
            if (level == null) {
                continue;
            }
            String key = levelKey(level);
            if (nether) {
                if (key.contains("the_nether")) {
                    return level;
                }
            } else if (key.contains("overworld")) {
                return level;
            }
        }
        return null;
    }

    static String levelName(Object nmsLevel) {
        for (World world : Bukkit.getWorlds()) {
            if (nmsWorld(world) == nmsLevel) {
                return world.getName();
            }
        }
        return String.valueOf(nmsLevel);
    }

    static String worldSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("allow-nether=").append(Bukkit.getAllowNether())
                .append(" worlds=").append(Bukkit.getWorlds().size());
        for (World world : Bukkit.getWorlds()) {
            sb.append("\n  - ").append(world.getName())
                    .append(" env=").append(world.getEnvironment())
                    .append(" key=").append(levelKey(nmsWorld(world)));
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------- precheck

    /**
     * Read-only replay of what {@code Entity.findDimensionEntryPoint} would do for this player:
     * scale the coordinates, resolve the destination level and search for a portal with
     * {@code PortalForcer.findPortalAround}. Never creates portals.
     */
    static String precheck(Player player) {
        StringBuilder sb = new StringBuilder("PRECHECK ");
        try {
            Object handle = nmsPlayer(player);
            Object level = nms(handle, "m_9236_", "level");
            String key = levelKey(level);
            boolean fromNether = key.contains("the_nether");
            boolean toNether = !fromNether;
            boolean vanillaSource = key.contains("minecraft:overworld") || key.contains("minecraft:the_nether");
            double scale = fromNether ? 8.0D : 0.125D;
            double x = player.getLocation().getX() * scale;
            double y = player.getLocation().getY();
            double z = player.getLocation().getZ() * scale;
            sb.append("fromKey=").append(key)
                    .append(" toNether=").append(toNether)
                    .append(" rawTarget=[").append(fmt(x)).append(',').append(fmt(y)).append(',').append(fmt(z)).append(']');
            if (!vanillaSource) {
                sb.append(" NOTE=custom-dimension-source (travel targets the VANILLA ")
                        .append(toNether ? "nether" : "overworld").append(" level)");
            }
            Object target = findLevel(toNether);
            if (target == null) {
                sb.append(" RESULT=FAIL destination vanilla level NOT LOADED -> silent abort");
                return sb.toString();
            }
            Object border = noArgReturning(target, "WorldBorder");
            Object forcer = noArgReturning(target, "PortalForcer");
            Class<?> blockPosClass = Class.forName("net.minecraft.core.BlockPos");
            Constructor<?> constructor = blockPosClass.getConstructor(int.class, int.class, int.class);
            Object blockPos = constructor.newInstance((int) Math.floor(x), (int) y, (int) Math.floor(z));
            int radius = toNether ? 16 : 128;
            Object result = searchPortal(forcer, blockPos, border, radius);
            sb.append(" target=").append(levelName(target)).append(" radius=").append(radius);
            if (border == null) {
                sb.append(" note=worldborder-unavailable");
            }
            if (result instanceof Optional) {
                Optional<?> optional = (Optional<?>) result;
                sb.append(" foundPortal=").append(optional.isPresent());
                if (optional.isPresent()) {
                    sb.append(" rect=").append(describeRectangle(optional.get()));
                } else {
                    sb.append(" (no portal in radius -> engine will try to create one at [")
                            .append((int) Math.floor(x)).append(',').append((int) y).append(',').append((int) Math.floor(z)).append(']');
                    if (radius == 128) {
                        sb.append(", creationRadius=16, canCreate depends on PlayerPortalEvent)");
                    } else {
                        sb.append(')');
                    }
                }
            } else if (result == null) {
                sb.append(" RESULT=UNKNOWN (portal search not callable) available=").append(forcerMethods(forcer));
            } else {
                sb.append(" RESULT=").append(result);
            }
            return sb.toString();
        } catch (Throwable error) {
            return sb.append(" RESULT=ERROR ").append(error).toString();
        }
    }

    /**
     * Calls the portal search in a read-only way, tolerating mapping differences:
     * 1. name "findPortalAround" with (BlockPos, WorldBorder, int)
     * 2. any 3-arg method (BlockPos, ?, int)
     * 3. the vanilla (BlockPos, boolean, WorldBorder) variant
     */
    private static Object searchPortal(Object forcer, Object blockPos, Object border, int radius) {
        if (forcer == null) {
            return null;
        }
        Object named = invoke(forcer, "findPortalAround", blockPos, border, radius);
        if (named != null) {
            return named;
        }
        for (Class<?> type = forcer.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                Class<?>[] params = method.getParameterTypes();
                if (params.length != 3 || params[2] != int.class) {
                    continue;
                }
                if (!params[0].getName().equals("net.minecraft.core.BlockPos")) {
                    continue;
                }
                try {
                    method.setAccessible(true);
                    return method.invoke(forcer, blockPos, border, radius);
                } catch (Throwable ignored) {
                }
            }
        }
        Object vanilla = invoke(forcer, "m_192985_", blockPos, Boolean.FALSE, border);
        if (vanilla == null) {
            vanilla = invoke(forcer, "findPortalAround", blockPos, Boolean.FALSE, border);
        }
        if (vanilla == null) {
            vanilla = invoke(forcer, "findClosestPortalPosition", blockPos, Boolean.FALSE, border);
        }
        return vanilla;
    }

    private static String forcerMethods(Object forcer) {
        if (forcer == null) {
            return "forcer=null";
        }
        StringBuilder sb = new StringBuilder();
        for (Class<?> type = forcer.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                if (method.getName().contains("ortal") || method.getName().startsWith("m_192")) {
                    sb.append(method.getName()).append('/').append(method.getParameterCount()).append(' ');
                }
            }
        }
        return sb.length() == 0 ? "none" : sb.toString().trim();
    }

    private static String describeRectangle(Object rectangle) {
        StringBuilder sb = new StringBuilder(rectangle.getClass().getSimpleName()).append('{');
        for (Field field : rectangle.getClass().getDeclaredFields()) {
            if (field.getType() == int.class) {
                try {
                    field.setAccessible(true);
                    sb.append(field.getName()).append('=').append(field.get(rectangle)).append(' ');
                } catch (Throwable ignored) {
                }
            }
        }
        return sb.append('}').toString();
    }

    static String describeEntity(Object nmsEntity) {
        if (nmsEntity == null) {
            return "null";
        }
        Object bukkit = nms(nmsEntity, "getBukkitEntity");
        String name = bukkit == null ? String.valueOf(nmsEntity) : String.valueOf(bukkit);
        return nmsEntity.getClass().getSimpleName() + "(" + name + ")";
    }

    // ---------------------------------------------------------------- listeners

    static String listeners() {
        StringBuilder sb = new StringBuilder("Listener report (Bukkit event -> plugins in registration order):");
        appendListeners(sb, "PlayerPortalEvent", PlayerPortalEvent.getHandlerList());
        appendListeners(sb, "PlayerTeleportEvent", PlayerTeleportEvent.getHandlerList());
        appendListeners(sb, "PlayerChangedWorldEvent", PlayerChangedWorldEvent.getHandlerList());
        appendListeners(sb, "EntityPortalEvent", EntityPortalEvent.getHandlerList());
        sb.append("\n  note: Forge listeners (EntityTravelToDimensionEvent) cannot be listed from Bukkit;")
                .append(" watch the [Forge/*] lines in this log instead.");
        return sb.toString();
    }

    static int listenerCount(HandlerList handlers) {
        try {
            return handlers.getRegisteredListeners().length;
        } catch (Throwable ignored) {
            return -1;
        }
    }

    private static void appendListeners(StringBuilder sb, String name, HandlerList handlers) {
        RegisteredListener[] list = handlers.getRegisteredListeners();
        sb.append("\n  ").append(name).append(": ").append(list.length).append(" listener(s)");
        for (RegisteredListener listener : list) {
            sb.append("\n    - ").append(listener.getPlugin().getName())
                    .append(" priority=").append(listener.getPriority())
                    .append(" class=").append(listener.getListener().getClass().getName());
        }
    }

    static String silentFailureHint(boolean eventSeen, boolean eventCancelled) {
        if (!eventSeen) {
            return "hint: no PlayerPortalEvent at all -> the portal was never recognized by the engine."
                    + " Check allow-nether, whether the world is the vanilla minecraft:overworld/the_nether,"
                    + " and whether the player actually stood inside the portal >= ~80 ticks (4s).";
        }
        if (eventCancelled) {
            return "hint: PlayerPortalEvent was CANCELLED -> run '/portalprobe listeners' and inspect every plugin"
                    + " with priority != MONITOR; also watch for [Forge/*] EntityTravelToDimensionEvent cancelled=true.";
        }
        return "hint: PlayerPortalEvent passed -> failure happens later (PlayerTeleportEvent cancel,"
                + " portalinfo==null silent return, or destination portal search/creation failure). See PRECHECK above.";
    }

    static String fmt(double value) {
        return String.format("%.1f", value);
    }
}
