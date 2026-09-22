package com.rosetta.remotedebugbridge.net;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.rosetta.remotedebugbridge.script.CompiledClass;
import com.rosetta.remotedebugbridge.script.DynamicClassLoader;
import com.rosetta.remotedebugbridge.script.JavaSourceCompiler;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
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
    private static final Map<ClassLoader, List<Object>> TRACKED_COMMANDS = new ConcurrentHashMap<>();
    private static final List<Object> SELF_CHECK_INSTANCES = new CopyOnWriteArrayList<>();
    private static final AtomicInteger SELF_CHECK_CANCELS = new AtomicInteger();
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

    // ------------------------------------------------------------------ script command lifecycle

    /**
     * Registers a Bukkit command (the script-side replacement for a bare
     * {@code CommandMap#register}) and tracks it by the class loader of the command
     * class. A later script reload unregisters exactly the commands registered by
     * the retired loader, so a reloaded script never leaves a duplicate behind.
     */
    public static synchronized String registerCommand(String fallbackPrefix, Object command) throws Exception {
        if (command == null) {
            throw new IllegalArgumentException("command is null");
        }
        Class<?> commandType = Reflect.load("org.bukkit.command.Command");
        if (!commandType.isInstance(command)) {
            throw new IllegalArgumentException("not a Bukkit Command: " + command.getClass().getName());
        }
        Object commandMap = commandMap();
        String name = String.valueOf(Reflect.call(command, "getName"));
        ClassLoader key = classLoaderOf(command);
        Object registered = Reflect.call(commandMap, "register",
                fallbackPrefix == null || fallbackPrefix.isBlank() ? "rosetta" : fallbackPrefix, command);
        TRACKED_COMMANDS.computeIfAbsent(key, ignored -> new CopyOnWriteArrayList<>()).add(command);
        return "registered " + name + " prefix=" + fallbackPrefix + " accepted=" + registered
                + " loader=" + key;
    }

    /**
     * Unregisters a command from the server command map (all of its aliases).
     * CraftCommandMap registers both {@code label} and {@code fallbackPrefix:label} keys,
     * so besides {@code Command#unregister} every knownCommands entry pointing at the
     * command instance is removed directly.
     */
    public static int unregisterCommand(Object command) {
        if (command == null) {
            return 0;
        }
        int removed = 0;
        try {
            Object map = commandMap();
            try {
                Reflect.call(command, "unregister", map);
            } catch (Throwable ignored) {
            }
            Object known = Reflect.field(map, "knownCommands");
            if (known instanceof Map<?, ?> knownMap) {
                List<Object> keys = new ArrayList<>();
                for (Map.Entry<?, ?> entry : knownMap.entrySet()) {
                    if (entry.getValue() == command) {
                        keys.add(entry.getKey());
                    }
                }
                for (Object key : keys) {
                    if (knownMap.remove(key) != null) {
                        removed++;
                    }
                }
            }
        } catch (Throwable error) {
            LOGGER.warn("unregister command failed: {}", error.toString());
        }
        return removed;
    }

    private static Object commandMap() throws Exception {
        Object map = Reflect.field(server(), "commandMap");
        if (map == null) {
            throw new IllegalStateException("server has no commandMap field");
        }
        return map;
    }

    private static ClassLoader classLoaderOf(Object instance) {
        ClassLoader key = instance.getClass().getClassLoader();
        return key == null ? BukkitAdapter.class.getClassLoader() : key;
    }

    /** Class loader of a loaded plugin, used for cross-loader API calls (CoderAdapter). */
    public static ClassLoader pluginClassLoader(String pluginName) throws Exception {
        Object plugin = Reflect.call(pluginManager(), "getPlugin", pluginName);
        if (plugin == null) {
            throw new IllegalStateException("plugin not found: " + pluginName);
        }
        return classLoaderOf(plugin);
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
     * Unregisters every Bukkit listener and command whose class was loaded from
     * {@code loader}. Listener cleanup covers adapter-tracked listeners and listeners
     * registered directly through ServerAPI.putBukkitEvents by scripts (scanned via
     * HandlerList.getRegisteredListeners for the owner plugin); command cleanup covers
     * commands registered through {@link #registerCommand}.
     *
     * @return {@code [listenersRemoved, commandsRemoved]}
     */
    public static synchronized int[] cleanupClassLoader(ClassLoader loader) {
        if (!present() || loader == null) {
            return new int[]{0, 0};
        }
        int removedListeners = 0;
        List<Object> tracked = TRACKED.remove(loader);
        if (tracked != null) {
            for (Object listener : tracked) {
                removedListeners += unregisterBukkitListener(listener);
            }
        }
        Object owner = ownerPlugin();
        if (owner != null) {
            try {
                Class<?> handlerList = Reflect.load("org.bukkit.event.HandlerList");
                Object registered = Reflect.callStatic(handlerList, "getRegisteredListeners", owner);
                if (registered instanceof List<?> list) {
                    for (Object registeredListener : list) {
                        Object listener = Reflect.call(registeredListener, "getListener");
                        if (listener != null && listener.getClass().getClassLoader() == loader) {
                            removedListeners += unregisterBukkitListener(listener);
                        }
                    }
                }
            } catch (Throwable error) {
                LOGGER.warn("class loader listener scan failed: {}", error.toString());
            }
        }
        int removedCommands = 0;
        List<Object> commands = TRACKED_COMMANDS.remove(loader);
        if (commands != null) {
            for (Object command : commands) {
                removedCommands += unregisterCommand(command);
            }
        }
        return new int[]{removedListeners, removedCommands};
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
        SELF_CHECK_INSTANCES.clear();
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
                + " selfCheckInstances=" + SELF_CHECK_INSTANCES.size()
                + " selfCheckCancels=" + SELF_CHECK_CANCELS.get()
                + " owner=" + (ownerPlugin() == null ? "none" : ownerPlugin().getClass().getName());
    }

    // ------------------------------------------------------------------ self check

    /** Increments the self-check cancellation counter; called from the generated listener. */
    public static void noteSelfCheckCancel() {
        SELF_CHECK_CANCELS.incrementAndGet();
    }

    /**
     * Re-registers the self-check listener after `listener cleanup` removed it.
     * Idempotent: does nothing when a self-check listener is already live.
     */
    public static synchronized String restoreSelfCheck() {
        if (!present()) {
            return "absent";
        }
        try {
            registerSelfCheckListener();
        } catch (Throwable error) {
            LOGGER.warn("self-check restore failed: {}", error.toString());
        }
        return describe();
    }

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
                + "        try { com.rosetta.remotedebugbridge.net.BukkitAdapter.noteSelfCheckCancel(); } catch (Throwable ignored) { }\n"
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
        SELF_CHECK_INSTANCES.add(listener);
        LOGGER.info("Self-check listener ready: {}", status);
    }

    private static synchronized JavaSourceCompiler compiler() {
        if (compiler == null) {
            compiler = new JavaSourceCompiler(new EclipseCompiler());
        }
        return compiler;
    }
}
