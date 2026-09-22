package com.rosetta.remotedebugbridge.net;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.rosetta.remotedebugbridge.script.CompiledClass;
import com.rosetta.remotedebugbridge.script.DynamicClassLoader;
import com.rosetta.remotedebugbridge.script.JavaSourceCompiler;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.rain.repack.ecj.internal.compiler.tool.EclipseCompiler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Mohist/Bukkit adaptation layer.
 *
 * Everything here is reflection based: the adapter is inert on a pure Forge server and
 * activates only when org.bukkit.Bukkit can actually be loaded. The single exception is
 * {@link NexusTask}, whose signature references org.bukkit.plugin.Plugin; the generated
 * exec wrapper implements that interface only when a Bukkit runtime is detected.
 *
 * Security boundary: this class can register/unregister listeners and hot-reload plugins.
 * It is therefore only reachable through the token-authenticated RemoteBridge.
 */
public final class BukkitAdapter {

    private static final Logger LOGGER = LogManager.getLogger("RosettaNexus/BukkitAdapter");

    private static final Map<ClassLoader, List<Object>> TRACKED = new ConcurrentHashMap<>();
    private static volatile Boolean present;
    private static volatile Object ownerPlugin;
    private static volatile Object selfCheckListener;
    private static volatile JavaSourceCompiler compiler;

    private BukkitAdapter() {
    }

    /** True when a live Bukkit server is reachable from this class loader. */
    public static boolean present() {
        Boolean cached = present;
        if (cached != null) {
            return cached;
        }
        boolean result;
        try {
            Reflect.load("org.bukkit.Bukkit");
            result = true;
        } catch (Throwable ignored) {
            result = false;
        }
        present = result;
        return result;
    }

    /** Called after the Forge server has started. Never throws. */
    public static synchronized String init() {
        if (!present()) {
            LOGGER.info("Bukkit runtime not detected - adapter disabled, TCP bridge remains available");
            return "absent";
        }
        try {
            Object owner = ownerPlugin();
            String ownerName = owner == null ? "none" : String.valueOf(Reflect.call(owner, "getName"));
            LOGGER.info("Bukkit runtime detected - adapter active (owner plugin: {})", ownerName);
            registerSelfCheckListener();
            return "active owner=" + ownerName;
        } catch (Throwable error) {
            LOGGER.warn("Bukkit adapter init failed (bridge stays up): {}", error.toString());
            return "failed: " + error;
        }
    }

    // ------------------------------------------------------------------ server probes

    static Object server() throws Exception {
        return Reflect.callStatic(Reflect.load("org.bukkit.Bukkit"), "getServer");
    }

    static Object pluginManager() throws Exception {
        return Reflect.call(server(), "getPluginManager");
    }

    public static Object ownerPlugin() {
        Object cached = ownerPlugin;
        if (cached != null) {
            return cached;
        }
        try {
            Object manager = pluginManager();
            Object mohist = Reflect.call(manager, "getPlugin", "Mohist");
            if (mohist != null) {
                ownerPlugin = mohist;
                return mohist;
            }
            Object plugins = Reflect.call(manager, "getPlugins");
            if (plugins instanceof Object[] array && array.length > 0) {
                ownerPlugin = array[0];
                return array[0];
            }
        } catch (Throwable error) {
            LOGGER.warn("could not resolve an owner plugin: {}", error.toString());
        }
        return null;
    }

    // ------------------------------------------------------------------ commands

    /** Dispatches a command through the Bukkit console sender. Must run on the server thread. */
    static boolean console(String command) throws Exception {
        Object server = server();
        Object sender = Reflect.call(server, "getConsoleSender");
        Object dispatched = Reflect.call(server, "dispatchCommand", sender, command);
        return Boolean.TRUE.equals(dispatched);
    }

    static JsonArray plugins() throws Exception {
        Object manager = pluginManager();
        Object plugins = Reflect.call(manager, "getPlugins");
        JsonArray array = new JsonArray();
        if (plugins instanceof Object[] values) {
            for (Object plugin : values) {
                JsonObject entry = new JsonObject();
                entry.addProperty("name", String.valueOf(Reflect.call(plugin, "getName")));
                Object description = Reflect.call(plugin, "getDescription");
                entry.addProperty("version", String.valueOf(Reflect.call(description, "getVersion")));
                entry.addProperty("enabled", Boolean.TRUE.equals(Reflect.call(plugin, "isEnabled")));
                array.add(entry);
            }
        }
        return array;
    }

    static JsonObject setEnabled(String name, boolean enable) throws Exception {
        Object manager = pluginManager();
        Object plugin = Reflect.call(manager, "getPlugin", name);
        if (plugin == null) {
            throw new IllegalStateException("plugin not found: " + name);
        }
        Reflect.call(manager, enable ? "enablePlugin" : "disablePlugin", plugin);
        JsonObject out = new JsonObject();
        out.addProperty("name", String.valueOf(Reflect.call(plugin, "getName")));
        out.addProperty("enabled", Boolean.TRUE.equals(Reflect.call(plugin, "isEnabled")));
        return out;
    }

    /** In-place plugin hot update: unload old, rewrite jar, load + enable new. Must run on server thread. */
    static JsonObject update(String name, Path jar, byte[] data) throws Exception {
        Object manager = pluginManager();
        Object old = Reflect.call(manager, "getPlugin", name);
        String report = old == null ? "not-loaded" : PluginReloader.unload(manager, old);
        Thread.sleep(400L);
        if (jar.getParent() != null) {
            Files.createDirectories(jar.getParent());
        }
        Files.write(jar, data);
        PluginReloader.clearJarCache(jar.toFile());
        Object loaded = Reflect.call(manager, "loadPlugin", jar.toFile());
        if (loaded != null) {
            Reflect.call(manager, "enablePlugin", loaded);
        }
        JsonObject out = new JsonObject();
        out.addProperty("unload", report);
        if (loaded == null) {
            out.addProperty("loaded", "null");
            out.addProperty("enabled", false);
        } else {
            Object description = Reflect.call(loaded, "getDescription");
            out.addProperty("loaded", Reflect.call(loaded, "getName") + " " + Reflect.call(description, "getVersion"));
            out.addProperty("enabled", Boolean.TRUE.equals(Reflect.call(loaded, "isEnabled")));
        }
        return out;
    }

    // ------------------------------------------------------------------ listener lifecycle

    /**
     * Registers a Bukkit listener owned by the Mohist plugin (via ServerAPI.putBukkitEvents
     * on Mohist, plugin manager otherwise) and tracks it by class loader so script reloads
     * can unregister exactly the listeners of the retired loader.
     */
    public static synchronized String registerBukkitListener(Object listener) throws Exception {
        if (listener == null) {
            throw new IllegalArgumentException("listener is null");
        }
        Class<?> listenerType = Reflect.load("org.bukkit.event.Listener");
        if (!listenerType.isInstance(listener)) {
            throw new IllegalArgumentException("not a Bukkit Listener: " + listener.getClass().getName());
        }
        Object owner = ownerPlugin();
        if (owner == null) {
            throw new IllegalStateException("no Bukkit plugin available to own the listener");
        }
        boolean viaMohist = false;
        try {
            Class<?> pluginType = Reflect.load("org.bukkit.plugin.Plugin");
            Class<?> serverApi = Reflect.load("com.mohistmc.api.ServerAPI");
            Method method = serverApi.getMethod("putBukkitEvents", listenerType, pluginType);
            method.invoke(null, listener, owner);
            viaMohist = true;
        } catch (ClassNotFoundException mohistMissing) {
            Reflect.call(pluginManager(), "registerEvents", listener, owner);
        }
        ClassLoader key = listener.getClass().getClassLoader();
        if (key == null) {
            key = BukkitAdapter.class.getClassLoader();
        }
        TRACKED.computeIfAbsent(key, ignored -> new CopyOnWriteArrayList<>()).add(listener);
        return "registered " + listener.getClass().getName() + " owner=" + Reflect.call(owner, "getName")
                + (viaMohist ? " via=ServerAPI" : " via=registerEvents");
    }

    public static int unregisterBukkitListener(Object listener) {
        if (listener == null) {
            return 0;
        }
        try {
            Class<?> handlerList = Reflect.load("org.bukkit.event.HandlerList");
            Reflect.callStatic(handlerList, "unregisterAll", listener);
            return 1;
        } catch (Throwable error) {
            LOGGER.warn("unregister listener failed: {}", error.toString());
            return 0;
        }
    }

    /**
     * Unregisters every Bukkit listener whose class was loaded from {@code loader}.
     * Covers both adapter-tracked listeners and listeners registered directly through
     * ServerAPI.putBukkitEvents by scripts (scanned via HandlerList.getRegisteredListeners
     * for the owner plugin).
     */
    public static synchronized int cleanupClassLoader(ClassLoader loader) {
        if (!present() || loader == null) {
            return 0;
        }
        int removed = 0;
        List<Object> tracked = TRACKED.remove(loader);
        if (tracked != null) {
            for (Object listener : tracked) {
                removed += unregisterBukkitListener(listener);
            }
        }
        Object owner = ownerPlugin();
        if (owner == null) {
            return removed;
        }
        try {
            Class<?> handlerList = Reflect.load("org.bukkit.event.HandlerList");
            Object registered = Reflect.callStatic(handlerList, "getRegisteredListeners", owner);
            if (registered instanceof List<?> list) {
                for (Object registeredListener : list) {
                    Object listener = Reflect.call(registeredListener, "getListener");
                    if (listener != null && listener.getClass().getClassLoader() == loader) {
                        removed += unregisterBukkitListener(listener);
                    }
                }
            }
        } catch (Throwable error) {
            LOGGER.warn("class loader listener scan failed: {}", error.toString());
        }
        return removed;
    }

    /** Removes every listener the adapter itself registered (used by the `listener cleanup` command). */
    public static synchronized int cleanupAll() {
        int removed = 0;
        for (List<Object> list : TRACKED.values()) {
            for (Object listener : list) {
                removed += unregisterBukkitListener(listener);
            }
        }
        TRACKED.clear();
        selfCheckListener = null;
        return removed;
    }

    public static synchronized String describe() {
        int tracked = 0;
        for (List<Object> list : TRACKED.values()) {
            tracked += list.size();
        }
        return "present=" + present() + " tracked=" + tracked
                + " selfCheck=" + (selfCheckListener != null)
                + " owner=" + (ownerPlugin() == null ? "none" : ownerPlugin().getClass().getName());
    }

    // ------------------------------------------------------------------ self check

    private static void registerSelfCheckListener() throws Exception {
        if (selfCheckListener != null) {
            return;
        }
        String simple = "NexusSelfCheck" + System.nanoTime();
        String fqcn = "rosetta.remote.generated." + simple;
        String source = ""
                + "package rosetta.remote.generated;\n"
                + "public class " + simple + " implements org.bukkit.event.Listener {\n"
                + "  @org.bukkit.event.EventHandler\n"
                + "  public void onEntitySpawn(org.bukkit.event.entity.EntitySpawnEvent event) {\n"
                + "    try {\n"
                + "      if (\"CREEPER\".equals(event.getEntityType().name())) {\n"
                + "        event.setCancelled(true);\n"
                + "        org.bukkit.Bukkit.getLogger().info(\"[RosettaNexus] self-check listener cancelled creeper spawn at \" + event.getLocation());\n"
                + "      }\n"
                + "    } catch (Throwable t) {\n"
                + "      org.bukkit.Bukkit.getLogger().warning(\"[RosettaNexus] self-check listener error: \" + t);\n"
                + "    }\n"
                + "  }\n"
                + "}\n";
        DynamicClassLoader loader = new DynamicClassLoader(Thread.currentThread().getContextClassLoader());
        CompiledClass compiled = compiler().compileFromString(fqcn, source);
        if (compiled == null || compiled.bytecode == null) {
            throw new IllegalStateException("self-check listener compilation produced no bytecode");
        }
        for (Map.Entry<String, byte[]> entry : compiled.allClasses.entrySet()) {
            loader.addCompiledClass(entry.getKey(), entry.getValue());
        }
        loader.addCompiledClass(compiled.className, compiled.bytecode);
        Object listener = loader.loadClass(compiled.className).getDeclaredConstructor().newInstance();
        String status = registerBukkitListener(listener);
        selfCheckListener = listener;
        LOGGER.info("Self-check listener ready: {}", status);
    }

    private static synchronized JavaSourceCompiler compiler() {
        if (compiler == null) {
            compiler = new JavaSourceCompiler(new EclipseCompiler());
        }
        return compiler;
    }
}
