package com.rosetta.remotedebugbridge.net;

import java.io.File;
import java.lang.reflect.Field;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Best-effort in-place Bukkit plugin reloader (reflection port of
 * remote-bridge/src/main/java/com/rosetta/remote/PluginReloader.java).
 *
 * Steps: disable -> unregister listeners -> drop its commands -> drop permissions ->
 * remove it from SimplePluginManager internals -> close its PluginClassLoader
 * (releases the jar file handle on Windows) -> clear the sun JarFileFactory cache.
 * The caller then writes the new jar and calls loadPlugin + enablePlugin.
 *
 * Every Bukkit call goes through {@link Reflect} so this class never links Bukkit at
 * class load time. It is only invoked when the adapter detected a live Bukkit server.
 */
final class PluginReloader {

    private PluginReloader() {
    }

    static String unload(Object manager, Object plugin) {
        List<String> notes = new ArrayList<>();
        try {
            Reflect.call(manager, "disablePlugin", plugin);
            notes.add("disabled");
        } catch (Throwable error) {
            notes.add("disable:" + error);
        }
        try {
            Class<?> handlerList = Reflect.load("org.bukkit.event.HandlerList");
            Reflect.callStatic(handlerList, "unregisterAll", plugin);
            notes.add("listeners");
        } catch (Throwable error) {
            notes.add("listeners:" + error);
        }
        try {
            int removed = 0;
            Object server = BukkitAdapter.server();
            Object commandMap = server == null ? null : Reflect.field(server, "commandMap");
            Object knownObject = commandMap == null ? null : Reflect.field(commandMap, "knownCommands");
            if (knownObject instanceof Map<?, ?> known) {
                List<Object> keys = new ArrayList<>();
                for (Map.Entry<?, ?> entry : known.entrySet()) {
                    Object value = entry.getValue();
                    if (value == null || !"org.bukkit.command.PluginCommand".equals(value.getClass().getName())) {
                        continue;
                    }
                    Object owner = Reflect.call(value, "getPlugin");
                    if (owner == plugin) {
                        keys.add(entry.getKey());
                    }
                }
                for (Object key : keys) {
                    known.remove(key);
                    removed++;
                }
            }
            notes.add("commands:" + removed);
        } catch (Throwable error) {
            notes.add("commands:" + error);
        }
        try {
            Object description = Reflect.call(plugin, "getDescription");
            Object permissions = Reflect.call(description, "getPermissions");
            if (permissions instanceof Iterable<?> iterable) {
                for (Object permission : iterable) {
                    try {
                        Reflect.call(manager, "removePermission", permission);
                    } catch (Throwable ignored) {
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            Object plugins = Reflect.field(manager, "plugins");
            if (plugins instanceof List<?> list) {
                for (Iterator<?> iterator = list.iterator(); iterator.hasNext(); ) {
                    if (iterator.next() == plugin) {
                        iterator.remove();
                    }
                }
            }
            String name = String.valueOf(Reflect.call(plugin, "getName"));
            removeKeyFromMapField(manager, "lookupNames", plugin);
            removeKeyFromMapField(manager, "lookupNames", name);
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

    private static void removeKeyFromMapField(Object target, String fieldName, Object keyOrValue) {
        try {
            Object value = Reflect.field(target, fieldName);
            if (value instanceof Map<?, ?> map) {
                List<Object> keys = new ArrayList<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (entry.getValue() == keyOrValue || entry.getKey().equals(keyOrValue)) {
                        keys.add(entry.getKey());
                    }
                }
                for (Object key : keys) {
                    map.remove(key);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * Drops cached jar handles so the file can be rewritten on Windows after the
     * plugin class loader has been closed.
     */
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
}
