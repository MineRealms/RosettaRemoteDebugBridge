package com.rosetta.remote;

import java.io.File;
import java.lang.reflect.Field;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.event.HandlerList;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

/**
 * Best-effort in-place plugin reloader for Bukkit/Mohist.
 *
 * Steps: disable -> unregister listeners -> drop its commands -> drop permissions ->
 * remove it from SimplePluginManager internals -> close its PluginClassLoader
 * (releases the jar file handle on Windows) -> clear the sun JarFileFactory cache ->
 * caller writes the new jar -> loadPlugin + enablePlugin.
 */
final class PluginReloader {

    private PluginReloader() {
    }

    static String unload(PluginManager manager, Plugin plugin) {
        List<String> notes = new ArrayList<>();
        try {
            manager.disablePlugin(plugin);
            notes.add("disabled");
        } catch (Throwable error) {
            notes.add("disable:" + error);
        }
        try {
            HandlerList.unregisterAll(plugin);
            notes.add("listeners");
        } catch (Throwable error) {
            notes.add("listeners:" + error);
        }
        try {
            int removed = 0;
            Object commandMapObject = commandMap();
            if (commandMapObject != null) {
                Object knownObject = readField(commandMapObject, "knownCommands");
                if (knownObject instanceof Map<?, ?> known) {
                    List<Object> keys = new ArrayList<>();
                    for (Map.Entry<?, ?> entry : known.entrySet()) {
                        if (entry.getValue() instanceof PluginCommand command && command.getPlugin() == plugin) {
                            keys.add(entry.getKey());
                        }
                    }
                    for (Object key : keys) {
                        known.remove(key);
                        removed++;
                    }
                }
            }
            notes.add("commands:" + removed);
        } catch (Throwable error) {
            notes.add("commands:" + error);
        }
        try {
            for (Permission permission : plugin.getDescription().getPermissions()) {
                manager.removePermission(permission);
            }
        } catch (Throwable ignored) {
        }
        try {
            removeFromListField(manager, "plugins", plugin);
            removeKeyFromMapField(manager, "lookupNames", plugin.getName());
            removeKeyFromMapField(manager, "lookupNames", plugin.getDescription().getName());
            notes.add("manager");
        } catch (Throwable error) {
            notes.add("manager:" + error);
        }
        try {
            ClassLoader loader = plugin.getClass().getClassLoader();
            if (loader instanceof URLClassLoader urlLoader) {
                urlLoader.close();
                notes.add("loader-closed");
            }
        } catch (Throwable error) {
            notes.add("loader:" + error);
        }
        return String.join(", ", notes);
    }

    static void clearJarCache(File jar) {
        try {
            URLConnection.setDefaultUseCaches("jar", false);
        } catch (Throwable ignored) {
        }
        try {
            Class<?> factory = Class.forName("sun.net.www.protocol.jar.JarFileFactory");
            String name = jar.getName();
            for (String fieldName : new String[]{"urlCache", "fileCache"}) {
                try {
                    Field field = factory.getDeclaredField(fieldName);
                    field.setAccessible(true);
                    Object value = field.get(null);
                    if (value instanceof Map<?, ?> map) {
                        map.entrySet().removeIf(entry -> String.valueOf(entry.getKey()).contains(name)
                                || String.valueOf(entry.getValue()).contains(name));
                    }
                } catch (Throwable ignoredInner) {
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static Object commandMap() {
        try {
            Field field = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            field.setAccessible(true);
            return field.get(Bukkit.getServer());
        } catch (Throwable error) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static void removeFromListField(Object target, String fieldName, Object element) {
        Object value = readField(target, fieldName);
        if (value instanceof List<?> list) {
            ((List<Object>) list).removeIf(entry -> entry == element);
        }
    }

    @SuppressWarnings("unchecked")
    private static void removeKeyFromMapField(Object target, String fieldName, String key) {
        Object value = readField(target, fieldName);
        if (value instanceof Map<?, ?> map) {
            ((Map<Object, Object>) map).remove(key);
        }
    }

    private static Object readField(Object target, String name) {
        for (Class<?> type = target.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (Throwable ignored) {
            }
        }
        return null;
    }
}
