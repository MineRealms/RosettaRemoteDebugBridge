package com.rosetta.remote;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class RemoteBridgePlugin extends JavaPlugin {

    private static final Gson GSON = new Gson();
    private static final String TOKEN_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";

    private RemoteServer server;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        String token = getConfig().getString("token", "");
        if (token == null || token.isBlank() || token.equals("change-me")) {
            token = randomToken();
            getConfig().set("token", token);
            saveConfig();
            getLogger().warning("Generated new remote token (also written to config.yml): " + token);
        }
        try {
            server = new RemoteServer(this);
            server.start();
        } catch (Throwable error) {
            getLogger().severe("cannot bind " + bind() + ":" + port() + " - " + error);
            setEnabled(false);
            return;
        }
        getLogger().warning("Remote bridge listening on " + bind() + ":" + port()
                + " - token auth, arbitrary code execution. Firewall the port or use an SSH tunnel. Remove when done.");
    }

    @Override
    public void onDisable() {
        if (server != null) {
            server.close();
            server = null;
        }
    }

    String bind() {
        return getConfig().getString("bind", "0.0.0.0");
    }

    int port() {
        return getConfig().getInt("port", 48790);
    }

    String token() {
        return getConfig().getString("token", "");
    }

    List<String> allowIps() {
        return getConfig().getStringList("allow-ips");
    }

    boolean allowCodeExec() {
        return getConfig().getBoolean("allow-code-exec", true);
    }

    boolean allowPathEscape() {
        return getConfig().getBoolean("allow-path-escape", false);
    }

    JsonElement dispatch(String cmd, JsonObject args, String remoteIp) throws Exception {
        switch (cmd) {
            case "ping": {
                JsonObject out = new JsonObject();
                out.addProperty("server", getServer().getName() + " " + getServer().getVersion());
                out.addProperty("java", System.getProperty("java.version"));
                out.addProperty("players", getServer().getOnlinePlayers().size());
                out.addProperty("plugins", getServer().getPluginManager().getPlugins().length);
                out.addProperty("remote", remoteIp);
                return out;
            }
            case "console": {
                String command = require(args, "command");
                return callSync(() -> {
                    List<String> captured = new ArrayList<>();
                    Handler handler = capture(captured);
                    Logger root = Logger.getLogger("");
                    root.addHandler(handler);
                    try {
                        boolean dispatched = getServer().dispatchCommand(getServer().getConsoleSender(), command);
                        Thread.sleep(200L);
                        JsonObject out = new JsonObject();
                        out.addProperty("dispatched", dispatched);
                        out.addProperty("output", String.join("\n", captured));
                        return out;
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        return null;
                    } finally {
                        root.removeHandler(handler);
                    }
                }, 30_000L);
            }
            case "exec": {
                if (!allowCodeExec()) {
                    throw new IllegalStateException("allow-code-exec is false in config.yml");
                }
                String code = require(args, "code");
                String[] extra = args.has("args") ? GSON.fromJson(args.get("args"), String[].class) : new String[0];
                return callSync(() -> {
                    JsonObject out = new JsonObject();
                    out.addProperty("result", JavaExecutor.run(this, code, extra));
                    return out;
                }, 60_000L);
            }
            case "reflect": {
                return reflect(args);
            }
            case "upload": {
                byte[] data = Base64.getDecoder().decode(require(args, "base64"));
                Path target = resolve(require(args, "path"));
                Files.createDirectories(target.getParent());
                Files.write(target, data);
                JsonObject out = new JsonObject();
                out.addProperty("path", target.toString());
                out.addProperty("bytes", data.length);
                return out;
            }
            case "load": {
                Path jar = resolve(require(args, "file"));
                if (!Files.isRegularFile(jar)) {
                    throw new IOException("no such file: " + jar);
                }
                return callSync(() -> {
                    try {
                        Plugin loaded = getServer().getPluginManager().loadPlugin(jar.toFile());
                        if (loaded == null) {
                            throw new IllegalStateException("loadPlugin returned null");
                        }
                        getServer().getPluginManager().enablePlugin(loaded);
                        JsonObject out = new JsonObject();
                        out.addProperty("name", loaded.getName());
                        out.addProperty("version", loaded.getDescription().getVersion());
                        out.addProperty("enabled", loaded.isEnabled());
                        return out;
                    } catch (Exception error) {
                        throw new RuntimeException(error);
                    }
                }, 60_000L);
            }
            case "reload": {
                String name = require(args, "name");
                Path jar = resolve(require(args, "file"));
                return callSync(() -> {
                    PluginManager manager = getServer().getPluginManager();
                    Plugin old = manager.getPlugin(name);
                    String report = old == null ? "not-loaded" : PluginReloader.unload(manager, old);
                    try {
                        Thread.sleep(400L);
                    } catch (InterruptedException ignored) {
                    }
                    PluginReloader.clearJarCache(jar.toFile());
                    Plugin loaded = manager.loadPlugin(jar.toFile());
                    if (loaded != null) {
                        manager.enablePlugin(loaded);
                    }
                    JsonObject out = new JsonObject();
                    out.addProperty("unload", report);
                    out.addProperty("loaded", loaded == null ? "null" : loaded.getName() + " " + loaded.getDescription().getVersion());
                    out.addProperty("enabled", loaded != null && loaded.isEnabled());
                    return out;
                }, 120_000L);
            }
            case "update": {
                String name = require(args, "name");
                Path jar = resolve(require(args, "path"));
                byte[] data = Base64.getDecoder().decode(require(args, "base64"));
                return callSync(() -> {
                    PluginManager manager = getServer().getPluginManager();
                    Plugin old = manager.getPlugin(name);
                    String report = old == null ? "not-loaded" : PluginReloader.unload(manager, old);
                    try {
                        Thread.sleep(400L);
                    } catch (InterruptedException ignored) {
                    }
                    Files.write(jar, data);
                    PluginReloader.clearJarCache(jar.toFile());
                    Plugin loaded = manager.loadPlugin(jar.toFile());
                    if (loaded != null) {
                        manager.enablePlugin(loaded);
                    }
                    JsonObject out = new JsonObject();
                    out.addProperty("unload", report);
                    out.addProperty("loaded", loaded == null ? "null" : loaded.getName() + " " + loaded.getDescription().getVersion());
                    out.addProperty("enabled", loaded != null && loaded.isEnabled());
                    return out;
                }, 120_000L);
            }
            case "enable":
            case "disable": {
                String name = require(args, "name");
                boolean enable = cmd.equals("enable");
                return callSync(() -> {
                    Plugin target = getServer().getPluginManager().getPlugin(name);
                    if (target == null) {
                        throw new IllegalStateException("plugin not found: " + name);
                    }
                    if (enable) {
                        getServer().getPluginManager().enablePlugin(target);
                    } else {
                        getServer().getPluginManager().disablePlugin(target);
                    }
                    JsonObject out = new JsonObject();
                    out.addProperty("name", target.getName());
                    out.addProperty("enabled", target.isEnabled());
                    return out;
                }, 30_000L);
            }
            case "plugins": {
                JsonArray array = new JsonArray();
                for (Plugin plugin : getServer().getPluginManager().getPlugins()) {
                    JsonObject entry = new JsonObject();
                    entry.addProperty("name", plugin.getName());
                    entry.addProperty("version", plugin.getDescription().getVersion());
                    entry.addProperty("enabled", plugin.isEnabled());
                    array.add(entry);
                }
                return array;
            }
            case "ls": {
                Path dir = resolve(require(args, "dir"));
                JsonArray array = new JsonArray();
                if (Files.isDirectory(dir)) {
                    try (var stream = Files.newDirectoryStream(dir)) {
                        for (Path path : stream) {
                            array.add(path.getFileName().toString() + (Files.isDirectory(path) ? "/" : ""));
                        }
                    }
                } else {
                    throw new IOException("not a directory: " + dir);
                }
                return array;
            }
            case "tail": {
                Path file = resolve(require(args, "file"));
                int lines = args.has("lines") ? args.get("lines").getAsInt() : 200;
                List<String> all = Files.readAllLines(file, StandardCharsets.UTF_8);
                int from = Math.max(0, all.size() - lines);
                JsonObject out = new JsonObject();
                out.addProperty("text", String.join("\n", all.subList(from, all.size())));
                return out;
            }
            case "read": {
                Path file = resolve(require(args, "file"));
                byte[] bytes = Files.readAllBytes(file);
                long offset = args.has("offset") ? args.get("offset").getAsLong() : 0L;
                int max = args.has("max") ? args.get("max").getAsInt() : 65536;
                int start = (int) Math.min(Math.max(0L, offset), bytes.length);
                int end = Math.min(bytes.length, start + max);
                JsonObject out = new JsonObject();
                out.addProperty("base64", Base64.getEncoder().encodeToString(Arrays.copyOfRange(bytes, start, end)));
                out.addProperty("size", bytes.length);
                return out;
            }
            default:
                throw new IllegalArgumentException("unknown cmd: " + cmd
                        + " (try: ping, console, exec, reflect, upload, load, enable, disable, plugins, ls, tail, read)");
        }
    }

    private JsonElement reflect(JsonObject args) throws Exception {
        String className = require(args, "class");
        String methodName = require(args, "method");
        Object target = null;
        if (args.has("target")) {
            switch (args.get("target").getAsString()) {
                case "server":
                    target = getServer();
                    break;
                case "nms-server":
                    target = getServer().getClass().getMethod("getServer").invoke(getServer());
                    break;
                case "plugin":
                    target = this;
                    break;
                default:
                    throw new IllegalArgumentException("target must be one of: server, nms-server, plugin");
            }
        }
        JsonArray input = args.has("args") ? args.getAsJsonArray("args") : new JsonArray();
        Object[] values = new Object[input.size()];
        Class<?>[] types = new Class<?>[input.size()];
        for (int i = 0; i < input.size(); i++) {
            JsonElement element = input.get(i);
            if (element.isJsonObject()) {
                JsonObject spec = element.getAsJsonObject();
                String type = spec.get("type").getAsString();
                String value = spec.has("value") ? spec.get("value").getAsString() : null;
                switch (type) {
                    case "string":
                        values[i] = value;
                        types[i] = String.class;
                        break;
                    case "int":
                        values[i] = Integer.parseInt(value);
                        types[i] = int.class;
                        break;
                    case "long":
                        values[i] = Long.parseLong(value);
                        types[i] = long.class;
                        break;
                    case "double":
                        values[i] = Double.parseDouble(value);
                        types[i] = double.class;
                        break;
                    case "float":
                        values[i] = Float.parseFloat(value);
                        types[i] = float.class;
                        break;
                    case "boolean":
                        values[i] = Boolean.parseBoolean(value);
                        types[i] = boolean.class;
                        break;
                    case "short":
                        values[i] = Short.parseShort(value);
                        types[i] = short.class;
                        break;
                    case "byte":
                        values[i] = Byte.parseByte(value);
                        types[i] = byte.class;
                        break;
                    case "char":
                        values[i] = value == null || value.isEmpty() ? '\0' : value.charAt(0);
                        types[i] = char.class;
                        break;
                    case "class":
                        types[i] = Class.forName(value, false, getClass().getClassLoader());
                        values[i] = null;
                        break;
                    case "null":
                        types[i] = null;
                        values[i] = null;
                        break;
                    default:
                        throw new IllegalArgumentException("bad arg type: " + type);
                }
            } else {
                values[i] = element.getAsString();
                types[i] = String.class;
            }
        }
        Class<?> clazz;
        try {
            clazz = Class.forName(className, false, getClass().getClassLoader());
        } catch (ClassNotFoundException notFound) {
            clazz = Class.forName(className);
        }
        java.lang.reflect.Method chosen = null;
        for (Class<?> type = clazz; type != null && chosen == null; type = type.getSuperclass()) {
            for (java.lang.reflect.Method method : type.getDeclaredMethods()) {
                if (!method.getName().equals(methodName) || method.getParameterCount() != values.length) {
                    continue;
                }
                Class<?>[] params = method.getParameterTypes();
                boolean ok = true;
                for (int i = 0; i < params.length && ok; i++) {
                    if (types[i] != null && !params[i].isAssignableFrom(types[i])) {
                        ok = false;
                    } else if (values[i] != null && !compatible(params[i], values[i])) {
                        ok = false;
                    }
                }
                if (ok) {
                    chosen = method;
                }
            }
        }
        if (chosen == null) {
            throw new NoSuchMethodException(className + "#" + methodName + " with " + values.length + " args");
        }
        final java.lang.reflect.Method method = chosen;
        final Object finalTarget = target;
        Object result = callSync(() -> {
            method.setAccessible(true);
            return method.invoke(finalTarget, values);
        }, 30_000L);
        JsonObject out = new JsonObject();
        out.addProperty("result", String.valueOf(result));
        return out;
    }

    private static boolean compatible(Class<?> param, Object value) {
        if (param.isInstance(value)) {
            return true;
        }
        if (!param.isPrimitive()) {
            return false;
        }
        return (param == int.class && value instanceof Integer)
                || (param == long.class && value instanceof Long)
                || (param == double.class && value instanceof Double)
                || (param == float.class && value instanceof Float)
                || (param == boolean.class && value instanceof Boolean)
                || (param == short.class && value instanceof Short)
                || (param == byte.class && value instanceof Byte)
                || (param == char.class && value instanceof Character);
    }

    private Path resolve(String path) throws IOException {
        Path root = new File(".").getCanonicalFile().toPath();
        Path target = root.resolve(path).normalize();
        if (!allowPathEscape() && !target.startsWith(root)) {
            throw new IOException("path escapes server root: " + path);
        }
        return target;
    }

    <T> T callSync(Callable<T> task, long timeoutMs) throws Exception {
        if (getServer().isPrimaryThread()) {
            return task.call();
        }
        FutureTask<T> future = new FutureTask<>(task);
        getServer().getScheduler().runTask(this, future);
        return future.get(timeoutMs, TimeUnit.MILLISECONDS);
    }

    private static Handler capture(List<String> sink) {
        return new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (record != null && record.getMessage() != null) {
                    sink.add(record.getLevel() + ": " + record.getMessage());
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
    }

    private static String require(JsonObject args, String key) {
        if (!args.has(key) || args.get(key).isJsonNull()) {
            throw new IllegalArgumentException("missing arg: " + key);
        }
        return args.get(key).getAsString();
    }

    private static String randomToken() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(28);
        for (int i = 0; i < 28; i++) {
            sb.append(TOKEN_CHARS.charAt(random.nextInt(TOKEN_CHARS.length())));
        }
        return sb.toString();
    }
}
