package com.rosetta.remotedebugbridge.debug.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.rosetta.remotedebugbridge.RosettaRemoteDebugBridge;
import com.rosetta.remotedebugbridge.debug.ClientDebugAgent;
import com.rosetta.remotedebugbridge.debug.ClientDebugHooks;
import com.rosetta.remotedebugbridge.debug.CrdPermission;
import com.rosetta.remotedebugbridge.debug.CrdProtocol;
import com.rosetta.remotedebugbridge.debug.CrdSessionRequest;
import java.io.File;
import java.net.HttpURLConnection;
import java.net.SocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.ModList;

/**
 * Client-only implementation of {@link ClientDebugHooks}: UI, info collection,
 * resource reload/push, whitelisted actions and SCRIPT confirmation.
 */
public final class ClientDebugClientBridge implements ClientDebugHooks {
    private static volatile ClientDebugClientBridge instance;

    private ClientDebugClientBridge() {
    }

    public static void install() {
        ClientDebugClientBridge current = instance;
        if (current == null) {
            synchronized (ClientDebugClientBridge.class) {
                current = instance;
                if (current == null) {
                    current = new ClientDebugClientBridge();
                    instance = current;
                }
            }
        }
        ClientDebugAgent.setHooks(current);
        RosettaRemoteDebugBridge.LOGGER.info("[CRD] client hooks installed");
    }

    @Override
    public String remoteAddress() {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.getConnection() != null && minecraft.getConnection().getConnection() != null) {
                SocketAddress address = minecraft.getConnection().getConnection().getRemoteAddress();
                if (address != null) {
                    return address.toString();
                }
            }
        }
        catch (Throwable ignored) {
        }
        return "unknown";
    }

    @Override
    public void requestSessionConfirm(CrdSessionRequest request, CrdPermission granted, Consumer<Boolean> decision) {
        Minecraft minecraft = Minecraft.getInstance();
        List<String> lines = List.of(
                "Server: " + request.getServerName(),
                "Address: " + request.getServerAddress(),
                "Identity: " + shortFingerprint(request.getFingerprint()),
                "Permission: " + granted + " (requested " + request.getRequestedPermission() + ")",
                "Timeout: " + request.getTimeoutSeconds() + "s",
                "",
                "Accepting allows encrypted remote debug for this session only.",
                "You can disconnect at any time with /crd disconnect.");
        minecraft.execute(() -> minecraft.setScreen(
                new ClientDebugConfirmScreen("Accept remote debug session?", lines, decision)));
    }

    @Override
    public void notifySession(boolean active, String summary) {
        ClientDebugHud.set(active, summary);
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            if (!active && minecraft.screen instanceof ClientDebugConfirmScreen) {
                minecraft.setScreen(null);
            }
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(Component.literal(summary), false);
            }
        });
    }

    @Override
    public void execute(String op, JsonObject args, Consumer<JsonObject> callback) {
        Minecraft minecraft = Minecraft.getInstance();
        switch (op) {
            case CrdProtocol.OP_COLLECT_INFO -> minecraft.execute(() -> callback.accept(collectInfo()));
            case CrdProtocol.OP_TAIL_LOG -> minecraft.execute(() -> callback.accept(tailLog(args)));
            case CrdProtocol.OP_RESOURCE_RELOAD -> minecraft.execute(() -> {
                minecraft.reloadResourcePacks();
                callback.accept(ok("resource reload triggered"));
            });
            case CrdProtocol.OP_PUSH_RESOURCE_PACK -> pushPack(args, callback);
            case CrdProtocol.OP_RUN_CLIENT_ACTION -> minecraft.execute(() -> callback.accept(runAction(args)));
            case CrdProtocol.OP_EVAL_CLIENT_SCRIPT -> confirmScript(args, callback);
            case CrdProtocol.OP_CLOSE -> {
                ClientDebugAgent.disconnect("server requested close");
                callback.accept(ok("session closed"));
            }
            default -> callback.accept(error("unsupported op: " + op));
        }
    }

    @Override
    public void tick() {
        // Agent handles idle expiry; HUD is driven by notifySession.
    }

    // ------------------------------------------------------------------ READ

    private JsonObject collectInfo() {
        Minecraft minecraft = Minecraft.getInstance();
        JsonObject result = new JsonObject();
        result.addProperty("mcVersion", SharedConstants.getCurrentVersion().getName());
        result.addProperty("side", "client");
        try {
            result.addProperty("player", minecraft.getUser() == null ? "unknown" : minecraft.getUser().getName());
        }
        catch (Throwable ignored) {
        }
        try {
            result.addProperty("modVersion", ModList.get().getModContainerById(RosettaRemoteDebugBridge.MOD_ID)
                    .map(container -> container.getModInfo().getVersion().toString()).orElse("unknown"));
        }
        catch (Throwable ignored) {
        }
        result.addProperty("fps", minecraft.getFps());
        Runtime runtime = Runtime.getRuntime();
        result.addProperty("memoryUsedMb", (runtime.totalMemory() - runtime.freeMemory()) / 1048576L);
        result.addProperty("memoryMaxMb", runtime.maxMemory() / 1048576L);
        try {
            if (minecraft.level != null) {
                result.addProperty("dimension", minecraft.level.dimension().location().toString());
            }
        }
        catch (Throwable ignored) {
        }
        try {
            JsonArray mods = new JsonArray();
            int count = 0;
            for (var mod : ModList.get().getMods()) {
                if (count++ >= 200) {
                    break;
                }
                mods.add(mod.getModId());
            }
            result.addProperty("modCount", ModList.get().getMods().size());
            result.add("mods", mods);
        }
        catch (Throwable ignored) {
        }
        try {
            JsonArray packs = new JsonArray();
            for (String id : minecraft.getResourcePackRepository().getSelectedIds()) {
                packs.add(id);
            }
            result.add("resourcePacks", packs);
        }
        catch (Throwable ignored) {
        }
        JsonObject session = new JsonObject();
        session.addProperty("active", ClientDebugAgent.isSessionActive());
        session.addProperty("server", ClientDebugAgent.getServerSummary());
        session.addProperty("permission", ClientDebugAgent.getGrantedPermission().name());
        result.add("session", session);
        result.add("logTail", readLogTail(50));
        return ok(result);
    }

    private JsonObject tailLog(JsonObject args) {
        int lines = args != null && args.has("lines") ? args.get("lines").getAsInt() : 100;
        lines = Math.max(1, Math.min(500, lines));
        JsonObject result = new JsonObject();
        result.addProperty("lines", lines);
        result.addProperty("text", readLogTailText(lines));
        return ok(result);
    }

    private JsonObject readLogTail(int lines) {
        JsonObject out = new JsonObject();
        out.addProperty("lines", lines);
        out.addProperty("text", readLogTailText(lines));
        return out;
    }

    private String readLogTailText(int lines) {
        try {
            Path log = Minecraft.getInstance().gameDirectory.toPath().resolve("logs").resolve("latest.log");
            if (!Files.exists(log)) {
                return "(no latest.log)";
            }
            List<String> all = Files.readAllLines(log, StandardCharsets.UTF_8);
            int from = Math.max(0, all.size() - lines);
            return String.join("\n", all.subList(from, all.size()));
        }
        catch (Throwable t) {
            return "(failed to read log: " + t + ")";
        }
    }

    // ------------------------------------------------------------------ RELOAD

    private void pushPack(JsonObject args, Consumer<JsonObject> callback) {
        if (args == null || !args.has("url")) {
            callback.accept(error("push_resource_pack requires 'url'"));
            return;
        }
        String urlText = args.get("url").getAsString();
        String expectedSha1 = args.has("sha1") ? args.get("sha1").getAsString() : "";
        String name = args.has("name") ? sanitize(args.get("name").getAsString()) : defaultPackName(urlText);
        Thread downloader = new Thread(() -> {
            try {
                byte[] data = download(urlText);
                if (!expectedSha1.isEmpty()) {
                    String actual = sha1Hex(data);
                    if (!actual.equalsIgnoreCase(expectedSha1)) {
                        callback.accept(error("sha1 mismatch: expected " + expectedSha1 + ", got " + actual));
                        return;
                    }
                }
                Minecraft minecraft = Minecraft.getInstance();
                Path dir = minecraft.gameDirectory.toPath().resolve("resourcepacks");
                Files.createDirectories(dir);
                Path target = dir.resolve(name);
                Files.write(target, data);
                minecraft.execute(() -> {
                    try {
                        var repository = minecraft.getResourcePackRepository();
                        repository.reload();
                        String base = name.endsWith(".zip") ? name.substring(0, name.length() - 4) : name;
                        String packId = base;
                        for (String id : repository.getAvailableIds()) {
                            if (id.equals(base) || id.endsWith("/" + name) || id.contains(base)) {
                                packId = id;
                                break;
                            }
                        }
                        boolean added = repository.addPack(packId);
                        minecraft.reloadResourcePacks();
                        JsonObject result = new JsonObject();
                        result.addProperty("file", target.toString());
                        result.addProperty("packId", packId);
                        result.addProperty("selected", added);
                        callback.accept(ok(result));
                    }
                    catch (Throwable t) {
                        callback.accept(error("pack install failed: " + t));
                    }
                });
            }
            catch (Throwable t) {
                callback.accept(error("download failed: " + t));
            }
        }, "RosettaCRD-Download");
        downloader.setDaemon(true);
        downloader.start();
    }

    private byte[] download(String urlText) throws Exception {
        HttpURLConnection connection = (HttpURLConnection)new URL(urlText).openConnection();
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(30_000);
        connection.setInstanceFollowRedirects(true);
        try (var in = connection.getInputStream()) {
            return in.readAllBytes();
        }
        finally {
            connection.disconnect();
        }
    }

    private static String defaultPackName(String urlText) {
        String base = urlText;
        int slash = base.lastIndexOf('/');
        if (slash >= 0) {
            base = base.substring(slash + 1);
        }
        if (base.isBlank() || !base.endsWith(".zip")) {
            base = "crd_pack_" + Math.abs(urlText.hashCode()) + ".zip";
        }
        return sanitize(base);
    }

    private static String sanitize(String name) {
        return name.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static String sha1Hex(byte[] data) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-1").digest(data);
        StringBuilder sb = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ ACTION (whitelist)

    private JsonObject runAction(JsonObject args) {
        String action = args != null && args.has("action") ? args.get("action").getAsString() : "";
        Minecraft minecraft = Minecraft.getInstance();
        try {
            return switch (action) {
                case "screenshot" -> {
                    Screenshot.grab(minecraft.gameDirectory, minecraft.getMainRenderTarget(), message -> {
                    });
                    yield ok("screenshot saved to screenshots/");
                }
                case "reload_resources" -> {
                    minecraft.reloadResourcePacks();
                    yield ok("resource reload triggered");
                }
                case "clear_chat" -> {
                    minecraft.gui.getChat().clearMessages(true);
                    yield ok("chat cleared");
                }
                case "disconnect" -> {
                    minecraft.getConnection().getConnection().disconnect(Component.literal("disconnected by CRD action"));
                    yield ok("disconnect requested");
                }
                default -> error("action not in whitelist: " + action);
            };
        }
        catch (Throwable t) {
            return error("action failed: " + t);
        }
    }

    // ------------------------------------------------------------------ SCRIPT (always confirmed)

    private void confirmScript(JsonObject args, Consumer<JsonObject> callback) {
        if (args == null || !args.has("source")) {
            callback.accept(error("eval_client_script requires 'source'"));
            return;
        }
        String source = args.get("source").getAsString();
        String name = args.has("name") ? args.get("name").getAsString() : "RemoteScript.java";
        String preview = source.length() > 400 ? source.substring(0, 400) + "..." : source;
        List<String> lines = List.of(
                "The server wants to execute a Java script on your client.",
                "Class: " + name,
                "This is executed once, with SCRIPT permission, and is fully audited.",
                "",
                "Preview:",
                preview);
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> minecraft.setScreen(new ClientDebugConfirmScreen(
                "Execute remote client script?",
                lines,
                accepted -> {
                    if (!accepted) {
                        callback.accept(error("script execution declined by player"));
                        return;
                    }
                    minecraft.execute(() -> callback.accept(ClientScriptRunner.run(source, name)));
                })));
    }

    // ------------------------------------------------------------------ helpers

    private static JsonObject ok(String message) {
        JsonObject result = new JsonObject();
        result.addProperty("message", message);
        return ok(result);
    }

    private static JsonObject ok(JsonObject result) {
        JsonObject out = new JsonObject();
        out.addProperty("ok", true);
        out.add("result", result);
        return out;
    }

    private static JsonObject error(String message) {
        JsonObject out = new JsonObject();
        out.addProperty("ok", false);
        out.addProperty("error", message);
        return out;
    }

    private static String shortFingerprint(String fingerprint) {
        if (fingerprint == null || fingerprint.isEmpty()) {
            return "unknown";
        }
        return fingerprint.length() > 16 ? fingerprint.substring(0, 16) + "..." : fingerprint;
    }
}
