package com.rosetta.remotedebugbridge.net;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rosetta.remotedebugbridge.RosettaRemoteDebugBridge;
import com.rosetta.remotedebugbridge.script.CompiledClass;
import com.rosetta.remotedebugbridge.script.DynamicClassLoader;
import com.rosetta.remotedebugbridge.script.JavaSourceCompiler;
import com.rosetta.remotedebugbridge.script.transformer.McpToSrgTransformer;
import com.rosetta.remotedebugbridge.script.utils.MinecraftHelper;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.loading.FMLPaths;
import net.rain.repack.ecj.internal.compiler.tool.EclipseCompiler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * RosettaNexus remote control plane.
 *
 * Newline-delimited JSON over TCP, wire compatible with tools/rosetta_remote.py:
 * request  {"token": "...", "cmd": "...", "args": {...}}
 * response {"ok": true, "result": ...} | {"ok": false, "error": "...", "cause": "..."}
 *
 * Security boundary (read before deploying):
 *  - the bridge is remote code execution by design; the token is mandatory and every
 *    request is checked against it. The default bind address is 127.0.0.1 (loopback)
 *    so the port is never exposed publicly; set -Drosetta.remote.bind=0.0.0.0 only
 *    behind a firewall / SSH tunnel.
 *  - the default port is 48791 and can be overridden with -Drosetta.remote.port.
 *  - the token can be forced with -Drosetta.remote.token; otherwise a random token is
 *    generated on first start and persisted to RosettaRemoteDebugBridge/remote-token.txt.
 *  - file commands (upload/read/tail/ls) are confined to the server root directory.
 *  - socket I/O runs on dedicated daemon threads and never blocks the server thread;
 *    only the actual command/plugin operations are scheduled onto the server thread.
 */
public final class RemoteBridge {

    private static final Logger LOGGER = LogManager.getLogger("RosettaNexus/Remote");

    public static final int DEFAULT_PORT = 48791;
    private static final int MAX_LINE = 64 * 1024 * 1024;
    private static final int MAX_RESULT = 200_000;
    private static final String TOKEN_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";

    private static volatile RemoteBridge instance;

    private final Gson gson = new Gson();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final String bind;
    private final int port;
    private final String token;

    private ServerSocket serverSocket;
    private MinecraftServer mcServer;
    private Thread serverThread;
    private volatile JavaSourceCompiler compiler;
    private volatile McpToSrgTransformer transformer;

    private RemoteBridge(String bind, int port, String token) {
        this.bind = bind;
        this.port = port;
        this.token = token;
    }

    public static boolean isRunning() {
        RemoteBridge current = instance;
        return current != null && current.running.get();
    }

    /** Starts the bridge once. Never throws; failures are logged so the server cannot crash. */
    public static synchronized void start(MinecraftServer server) {
        if (instance != null) {
            return;
        }
        try {
            String bind = System.getProperty("rosetta.remote.bind", "127.0.0.1");
            int port = Integer.getInteger("rosetta.remote.port", DEFAULT_PORT);
            RemoteBridge bridge = new RemoteBridge(bind, port, resolveToken());
            if (!bridge.startInternal(server)) {
                return;
            }
            instance = bridge;
        } catch (Throwable error) {
            LOGGER.error("Remote bridge failed to start (server keeps running)", error);
        }
    }

    public static synchronized void stop() {
        RemoteBridge current = instance;
        if (current == null) {
            return;
        }
        current.running.set(false);
        try {
            if (current.serverSocket != null) {
                current.serverSocket.close();
            }
        } catch (IOException ignored) {
        }
        instance = null;
        LOGGER.info("Remote bridge stopped");
    }

    private boolean startInternal(MinecraftServer server) {
        this.mcServer = server;
        this.serverThread = Thread.currentThread();
        try {
            ServerSocket socket = new ServerSocket();
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(bind, port));
            this.serverSocket = socket;
        } catch (Throwable error) {
            LOGGER.error("cannot bind {}:{} - {} (bridge disabled)", bind, port, error.toString());
            return false;
        }
        running.set(true);
        Thread accept = new Thread(this::acceptLoop, "RosettaNexus-Accept");
        accept.setDaemon(true);
        accept.start();
        LOGGER.warn("Remote bridge listening on {}:{} - token auth enforced, loopback-only by default."
                + " This port is arbitrary code execution: firewall it or use an SSH tunnel. Ports can be overridden"
                + " with -Drosetta.remote.bind / -Drosetta.remote.port.", bind, port);
        try {
            String adapter = BukkitAdapter.init();
            LOGGER.info("Bukkit adapter: {}", adapter);
        } catch (Throwable error) {
            LOGGER.warn("Bukkit adapter init error (bridge stays up): {}", error.toString());
        }
        return true;
    }

    private static String resolveToken() throws IOException {
        String forced = System.getProperty("rosetta.remote.token");
        if (forced != null && !forced.isBlank()) {
            LOGGER.info("Remote token loaded from -Drosetta.remote.token ({} chars)", forced.length());
            return forced;
        }
        Path gameDir = FMLPaths.GAMEDIR.get();
        Path tokenFile = gameDir.resolve("RosettaRemoteDebugBridge").resolve("remote-token.txt");
        if (Files.exists(tokenFile)) {
            String stored = Files.readString(tokenFile, StandardCharsets.UTF_8).trim();
            if (!stored.isBlank()) {
                LOGGER.info("Remote token loaded from {}", tokenFile);
                return stored;
            }
        }
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(28);
        for (int i = 0; i < 28; i++) {
            sb.append(TOKEN_CHARS.charAt(random.nextInt(TOKEN_CHARS.length())));
        }
        String generated = sb.toString();
        Files.createDirectories(tokenFile.getParent());
        Files.writeString(tokenFile, generated + System.lineSeparator(), StandardCharsets.UTF_8);
        LOGGER.warn("Generated remote token: {} (also written to {})", generated, tokenFile);
        return generated;
    }

    // ------------------------------------------------------------------ transport

    private void acceptLoop() {
        while (running.get()) {
            try {
                Socket client = serverSocket.accept();
                Thread worker = new Thread(() -> handle(client), "RosettaNexus-Conn");
                worker.setDaemon(true);
                worker.start();
            } catch (IOException error) {
                if (running.get()) {
                    LOGGER.warn("accept failed: {}", error.toString());
                }
            }
        }
    }

    private void handle(Socket client) {
        String ip = client.getInetAddress().getHostAddress();
        LOGGER.info("connection from {}", ip);
        AtomicInteger authFails = new AtomicInteger();
        try (Socket socket = client;
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)) {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                JsonObject response = new JsonObject();
                if (line.length() > MAX_LINE) {
                    response.addProperty("ok", false);
                    response.addProperty("error", "line too long");
                    out.println(response);
                    continue;
                }
                try {
                    JsonObject request = JsonParser.parseString(line).getAsJsonObject();
                    String supplied = request.has("token") ? request.get("token").getAsString() : "";
                    if (!token.equals(supplied)) {
                        response.addProperty("ok", false);
                        response.addProperty("error", "bad token");
                        out.println(response);
                        if (authFails.incrementAndGet() >= 3) {
                            break;
                        }
                        continue;
                    }
                    authFails.set(0);
                    String cmd = request.has("cmd") ? request.get("cmd").getAsString() : "";
                    JsonObject args = request.has("args") && request.get("args").isJsonObject()
                            ? request.getAsJsonObject("args") : new JsonObject();
                    LOGGER.info("{} -> {}", ip, cmd);
                    JsonElement result = dispatch(cmd, args);
                    response.addProperty("ok", true);
                    response.add("result", result == null ? JsonNull.INSTANCE : result);
                } catch (Throwable error) {
                    Throwable cause = error.getCause();
                    response.addProperty("ok", false);
                    response.addProperty("error", error.getMessage() != null ? error.getMessage() : String.valueOf(error));
                    response.addProperty("exception", error.getClass().getName());
                    if (cause != null) {
                        response.addProperty("cause", cause.getMessage() != null ? cause.getMessage() : String.valueOf(cause));
                    }
                }
                out.println(response);
            }
        } catch (Throwable error) {
            LOGGER.warn("connection {} error: {}", ip, error.toString());
        } finally {
            LOGGER.info("disconnected {}", ip);
        }
    }

    // ------------------------------------------------------------------ dispatch

    private JsonElement dispatch(String cmd, JsonObject args) throws Throwable {
        switch (cmd) {
            case "ping":
                return ping();
            case "console":
                return console(require(args, "command"));
            case "exec":
                return exec(require(args, "code"),
                        args.has("args") ? gson.fromJson(args.get("args"), String[].class) : new String[0]);
            case "reflect":
                return reflect(args);
            case "upload":
                return upload(require(args, "path"), require(args, "base64"));
            case "read":
                return read(require(args, "file"),
                        args.has("offset") ? args.get("offset").getAsLong() : 0L,
                        args.has("max") ? args.get("max").getAsInt() : 65536);
            case "tail":
                return tail(require(args, "file"), args.has("lines") ? args.get("lines").getAsInt() : 200);
            case "ls":
                return ls(require(args, "dir"));
            case "plugins":
                return plugins();
            case "enable":
            case "disable":
                return setEnabled(require(args, "name"), cmd.equals("enable"));
            case "update":
                return update(require(args, "name"), require(args, "path"), require(args, "base64"));
            case "listener":
                return listener(args.has("action") ? args.get("action").getAsString() : "status");
            default:
                throw new IllegalArgumentException("unknown cmd: " + cmd
                        + " (try: ping, console, exec, reflect, upload, read, tail, ls, plugins, enable, disable, update, listener)");
        }
    }

    private JsonObject ping() throws Exception {
        JsonObject out = new JsonObject();
        out.addProperty("server", System.getProperty("rosetta.remote.bind", "127.0.0.1") + ":" + port);
        out.addProperty("bridge", "rosetta-nexus " + RosettaRemoteDebugBridge.MOD_ID);
        out.addProperty("java", System.getProperty("java.version"));
        out.addProperty("bukkit", BukkitAdapter.present());
        out.addProperty("adapter", BukkitAdapter.describe());
        out.addProperty("players", onlinePlayers());
        out.addProperty("plugins", BukkitAdapter.present() ? pluginCount() : 0);
        out.addProperty("running", running.get());
        return out;
    }

    private int onlinePlayers() {
        if (!BukkitAdapter.present()) {
            return 0;
        }
        try {
            Object players = Reflect.call(BukkitAdapter.server(), "getOnlinePlayers");
            if (players instanceof List<?> list) {
                return list.size();
            }
            if (players instanceof Object[] array) {
                return array.length;
            }
        } catch (Throwable ignored) {
        }
        return 0;
    }

    private int pluginCount() {
        try {
            Object plugins = Reflect.call(BukkitAdapter.pluginManager(), "getPlugins");
            if (plugins instanceof Object[] array) {
                return array.length;
            }
        } catch (Throwable ignored) {
        }
        return 0;
    }

    // ------------------------------------------------------------------ commands

    private JsonObject console(String command) throws Throwable {
        return onServerThread(() -> {
            JsonObject out = new JsonObject();
            Path log = logFile();
            long before = fileSize(log);
            boolean dispatched;
            if (BukkitAdapter.present()) {
                dispatched = BukkitAdapter.console(command);
            } else {
                dispatched = forgeConsole(command);
            }
            Thread.sleep(250L);
            out.addProperty("dispatched", dispatched);
            out.addProperty("output", readFrom(log, before));
            return out;
        }, 30_000L);
    }

    /**
     * Pure Forge fallback (no Bukkit). Uses the vanilla command dispatcher directly.
     * Compiled against official names; reobfJar rewrites them for the SRG runtime.
     */
    private boolean forgeConsole(String command) {
        MinecraftServer server = mcServer;
        if (server == null) {
            return false;
        }
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
        return true;
    }

    private JsonObject exec(String code, String[] extra) throws Throwable {
        String body = (code == null || code.isBlank()) ? "return null;" : code;
        String simple = "RosettaTask" + System.nanoTime();
        String fqcn = "rosetta.remote.generated." + simple;
        boolean bukkit = BukkitAdapter.present();
        StringBuilder source = new StringBuilder(1024);
        source.append("package rosetta.remote.generated;\n");
        source.append("import java.util.*;\n");
        source.append("import java.util.stream.*;\n");
        source.append("import java.lang.reflect.*;\n");
        if (bukkit) {
            source.append("import org.bukkit.*;\n");
            source.append("import org.bukkit.entity.*;\n");
            source.append("import org.bukkit.event.*;\n");
            source.append("import org.bukkit.plugin.*;\n");
        }
        source.append("public class ").append(simple);
        if (bukkit) {
            source.append(" implements com.rosetta.remotedebugbridge.net.NexusTask");
        }
        source.append(" {\n");
        if (bukkit) {
            source.append("  public Object run(org.bukkit.plugin.Plugin plugin, Object[] args) throws Throwable {\n");
        } else {
            source.append("  public Object run(Object plugin, Object[] args) throws Throwable {\n");
        }
        source.append(body).append("\n  }\n}\n");
        String code2 = source.toString();
        if (MinecraftHelper.isSrgRuntime()) {
            try {
                code2 = transformer().transformSource(code2, simple + ".java");
            } catch (Throwable error) {
                LOGGER.warn("exec MCP->SRG transform failed, compiling original source: {}", error.toString());
            }
        }
        CompiledClass compiled = compiler().compileFromString(fqcn, code2);
        if (compiled == null || compiled.bytecode == null || compiled.bytecode.length == 0) {
            throw new IllegalStateException("compilation produced no bytecode for " + fqcn);
        }
        DynamicClassLoader loader = new DynamicClassLoader(Thread.currentThread().getContextClassLoader());
        for (Map.Entry<String, byte[]> entry : compiled.allClasses.entrySet()) {
            loader.addCompiledClass(entry.getKey(), entry.getValue());
        }
        loader.addCompiledClass(compiled.className, compiled.bytecode);
        final Class<?> taskClass = loader.loadClass(compiled.className);
        final Object[] taskArgs = extra == null ? new Object[0] : extra;
        final Object pluginObject = bukkit ? BukkitAdapter.ownerPlugin() : null;
        long start = System.currentTimeMillis();
        Object result = onServerThread(() -> {
            Object task = taskClass.getDeclaredConstructor().newInstance();
            java.lang.reflect.Method run = null;
            for (java.lang.reflect.Method method : taskClass.getMethods()) {
                if (method.getName().equals("run") && method.getParameterCount() == 2) {
                    run = method;
                    break;
                }
            }
            if (run == null) {
                throw new IllegalStateException("generated task has no run(plugin,args) method");
            }
            try {
                return run.invoke(task, pluginObject, taskArgs);
            } catch (java.lang.reflect.InvocationTargetException wrapped) {
                throw wrapped.getCause() != null ? wrapped.getCause() : wrapped;
            }
        }, 60_000L);
        String text = result == null ? "null" : String.valueOf(result);
        if (text.length() > MAX_RESULT) {
            text = text.substring(0, MAX_RESULT) + "...(truncated)";
        }
        JsonObject out = new JsonObject();
        out.addProperty("result", text + "  [" + (System.currentTimeMillis() - start) + "ms]");
        out.addProperty("class", fqcn);
        return out;
    }

    private JsonObject reflect(JsonObject args) throws Throwable {
        String className = require(args, "class");
        String methodName = require(args, "method");
        Object target = null;
        if (args.has("target")) {
            switch (args.get("target").getAsString()) {
                case "server":
                    target = BukkitAdapter.present() ? BukkitAdapter.server() : null;
                    break;
                case "nms-server":
                    target = mcServer;
                    break;
                case "plugin":
                    target = BukkitAdapter.ownerPlugin();
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
                    case "string" -> {
                        values[i] = value;
                        types[i] = String.class;
                    }
                    case "int" -> {
                        values[i] = Integer.parseInt(value);
                        types[i] = int.class;
                    }
                    case "long" -> {
                        values[i] = Long.parseLong(value);
                        types[i] = long.class;
                    }
                    case "double" -> {
                        values[i] = Double.parseDouble(value);
                        types[i] = double.class;
                    }
                    case "float" -> {
                        values[i] = Float.parseFloat(value);
                        types[i] = float.class;
                    }
                    case "boolean" -> {
                        values[i] = Boolean.parseBoolean(value);
                        types[i] = boolean.class;
                    }
                    case "short" -> {
                        values[i] = Short.parseShort(value);
                        types[i] = short.class;
                    }
                    case "byte" -> {
                        values[i] = Byte.parseByte(value);
                        types[i] = byte.class;
                    }
                    case "char" -> {
                        values[i] = value == null || value.isEmpty() ? '\0' : value.charAt(0);
                        types[i] = char.class;
                    }
                    case "class" -> {
                        types[i] = Reflect.load(value);
                        values[i] = null;
                    }
                    case "null" -> {
                        types[i] = null;
                        values[i] = null;
                    }
                    default -> throw new IllegalArgumentException("bad arg type: " + type);
                }
            } else {
                values[i] = element.getAsString();
                types[i] = String.class;
            }
        }
        Class<?> clazz = Reflect.load(className);
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
        Object result = onServerThread(() -> {
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

    private JsonObject upload(String path, String base64) throws Exception {
        byte[] data = Base64.getDecoder().decode(base64);
        Path target = resolve(path);
        if (target.getParent() != null) {
            Files.createDirectories(target.getParent());
        }
        Files.write(target, data);
        JsonObject out = new JsonObject();
        out.addProperty("path", target.toString());
        out.addProperty("bytes", data.length);
        return out;
    }

    private JsonObject read(String file, long offset, int max) throws Exception {
        Path target = resolve(file);
        byte[] bytes = Files.readAllBytes(target);
        int start = (int) Math.min(Math.max(0L, offset), bytes.length);
        int end = Math.min(bytes.length, start + Math.max(0, max));
        JsonObject out = new JsonObject();
        out.addProperty("base64", Base64.getEncoder().encodeToString(java.util.Arrays.copyOfRange(bytes, start, end)));
        out.addProperty("size", bytes.length);
        return out;
    }

    private JsonObject tail(String file, int lines) throws Exception {
        Path target = resolve(file);
        List<String> all = Files.readAllLines(target, StandardCharsets.UTF_8);
        int from = Math.max(0, all.size() - Math.max(0, lines));
        JsonObject out = new JsonObject();
        out.addProperty("text", String.join("\n", all.subList(from, all.size())));
        return out;
    }

    private JsonArray ls(String dir) throws Exception {
        Path target = resolve(dir);
        JsonArray array = new JsonArray();
        if (!Files.isDirectory(target)) {
            throw new IOException("not a directory: " + target);
        }
        try (var stream = Files.newDirectoryStream(target)) {
            for (Path path : stream) {
                array.add(path.getFileName().toString() + (Files.isDirectory(path) ? "/" : ""));
            }
        }
        return array;
    }

    private JsonElement plugins() throws Throwable {
        if (BukkitAdapter.present()) {
            return onServerThread(BukkitAdapter::plugins, 30_000L);
        }
        JsonArray array = new JsonArray();
        try {
            Class<?> modList = Reflect.load("net.minecraftforge.fml.ModList");
            Object list = Reflect.callStatic(modList, "get");
            Object mods = Reflect.call(list, "getMods");
            if (mods instanceof List<?> values) {
                for (Object mod : values) {
                    JsonObject entry = new JsonObject();
                    entry.addProperty("name", String.valueOf(Reflect.call(mod, "getModId")));
                    entry.addProperty("version", String.valueOf(Reflect.call(mod, "getVersion")));
                    entry.addProperty("enabled", true);
                    array.add(entry);
                }
            }
        } catch (Throwable error) {
            LOGGER.warn("plugins fallback failed: {}", error.toString());
        }
        return array;
    }

    private JsonObject setEnabled(String name, boolean enable) throws Throwable {
        if (!BukkitAdapter.present()) {
            throw new IllegalStateException("Bukkit is not present on this server");
        }
        return onServerThread(() -> BukkitAdapter.setEnabled(name, enable), 30_000L);
    }

    private JsonObject update(String name, String path, String base64) throws Throwable {
        if (!BukkitAdapter.present()) {
            throw new IllegalStateException("Bukkit is not present on this server");
        }
        byte[] data = Base64.getDecoder().decode(base64);
        Path jar = resolve(path);
        return onServerThread(() -> BukkitAdapter.update(name, jar, data), 120_000L);
    }

    private JsonObject listener(String action) throws Throwable {
        JsonObject out = new JsonObject();
        switch (action) {
            case "status" -> out.addProperty("status", BukkitAdapter.describe());
            case "cleanup" -> {
                int removed = BukkitAdapter.cleanupAll();
                out.addProperty("removed", removed);
                out.addProperty("status", BukkitAdapter.describe());
            }
            default -> throw new IllegalArgumentException("action must be status|cleanup");
        }
        return out;
    }

    // ------------------------------------------------------------------ helpers

    @FunctionalInterface
    private interface Task<T> {
        T call() throws Throwable;
    }

    private <T> T onServerThread(Task<T> task, long timeoutMs) throws Throwable {
        MinecraftServer server = this.mcServer;
        if (server == null || Thread.currentThread() == this.serverThread) {
            return task.call();
        }
        CompletableFuture<T> future = new CompletableFuture<>();
        server.execute(() -> {
            try {
                future.complete(task.call());
            } catch (Throwable error) {
                future.completeExceptionally(error);
            }
        });
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (ExecutionException error) {
            throw error.getCause() != null ? error.getCause() : error;
        }
    }

    private synchronized JavaSourceCompiler compiler() {
        if (compiler == null) {
            compiler = new JavaSourceCompiler(new EclipseCompiler());
        }
        return compiler;
    }

    private synchronized McpToSrgTransformer transformer() {
        if (transformer == null) {
            transformer = new McpToSrgTransformer();
        }
        return transformer;
    }

    private Path root() {
        try {
            return new File(".").getCanonicalFile().toPath();
        } catch (IOException error) {
            return Paths.get(".").toAbsolutePath().normalize();
        }
    }

    private Path resolve(String path) throws IOException {
        Path root = root();
        Path target = root.resolve(path).normalize();
        if (!target.startsWith(root) && !Boolean.getBoolean("rosetta.remote.allowPathEscape")) {
            throw new IOException("path escapes server root: " + path);
        }
        return target;
    }

    private Path logFile() {
        try {
            Path gameDir = FMLPaths.GAMEDIR.get();
            Path candidate = gameDir.resolve("logs").resolve("latest.log");
            if (Files.exists(candidate)) {
                return candidate;
            }
        } catch (Throwable ignored) {
        }
        return Paths.get("logs", "latest.log");
    }

    private static long fileSize(Path file) {
        try {
            return Files.size(file);
        } catch (IOException error) {
            return -1L;
        }
    }

    private static String readFrom(Path file, long offset) {
        try {
            if (!Files.exists(file)) {
                return "";
            }
            long size = Files.size(file);
            long start = (offset > 0 && offset <= size) ? offset : Math.max(0L, size - 65536L);
            long length = Math.min(size - start, 262144L);
            if (length <= 0) {
                return "";
            }
            byte[] buffer = new byte[(int) length];
            try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
                raf.seek(start);
                raf.readFully(buffer);
            }
            String text = new String(buffer, StandardCharsets.UTF_8);
            String[] lines = text.split("\r?\n");
            if (lines.length > 200) {
                StringBuilder tail = new StringBuilder();
                for (int i = lines.length - 200; i < lines.length; i++) {
                    tail.append(lines[i]).append('\n');
                }
                return tail.toString();
            }
            return text;
        } catch (Throwable error) {
            return "";
        }
    }

    private static String require(JsonObject args, String key) {
        if (!args.has(key) || args.get(key).isJsonNull()) {
            throw new IllegalArgumentException("missing arg: " + key);
        }
        return args.get(key).getAsString();
    }
}
