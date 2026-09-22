package com.rosetta.remote;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Newline-delimited JSON over TCP. Every request carries {"token","cmd","args"}. */
final class RemoteServer implements Runnable, Closeable {

    private static final int MAX_LINE = 64 * 1024 * 1024;

    private final RemoteBridgePlugin plugin;
    private final ServerSocket socket;
    private volatile boolean running;

    RemoteServer(RemoteBridgePlugin plugin) {
        this.plugin = plugin;
        try {
            this.socket = new ServerSocket();
        } catch (IOException error) {
            throw new RuntimeException("cannot create server socket", error);
        }
    }

    void start() throws IOException {
        socket.setReuseAddress(true);
        socket.bind(new InetSocketAddress(plugin.bind(), plugin.port()));
        running = true;
        Thread thread = new Thread(this, "RosettaRemote");
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public void run() {
        while (running) {
            try {
                Socket client = socket.accept();
                if (!allowed(client)) {
                    client.close();
                    continue;
                }
                Thread worker = new Thread(() -> handle(client), "RosettaRemote-conn");
                worker.setDaemon(true);
                worker.start();
            } catch (IOException error) {
                if (running) {
                    plugin.getLogger().warning("[Remote] accept failed: " + error);
                }
            }
        }
    }

    private boolean allowed(Socket client) {
        List<String> allow = plugin.allowIps();
        if (allow == null || allow.isEmpty()) {
            return true;
        }
        String ip = client.getInetAddress().getHostAddress();
        for (String entry : allow) {
            if (entry.equals(ip)) {
                return true;
            }
        }
        plugin.getLogger().warning("[Remote] rejected connection from " + ip + " (not in allow-ips)");
        return false;
    }

    private void handle(Socket client) {
        String ip = client.getInetAddress().getHostAddress();
        AtomicInteger authFails = new AtomicInteger();
        try (Socket c = client;
             BufferedReader in = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter out = new PrintWriter(new OutputStreamWriter(c.getOutputStream(), StandardCharsets.UTF_8), true)) {
            plugin.getLogger().info("[Remote] connection from " + ip);
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
                    String token = request.has("token") ? request.get("token").getAsString() : "";
                    if (!plugin.token().equals(token)) {
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
                    plugin.getLogger().info("[Remote] " + ip + " -> " + cmd);
                    JsonElement result = plugin.dispatch(cmd, args, ip);
                    response.addProperty("ok", true);
                    response.add("result", result == null ? JsonNull.INSTANCE : result);
                } catch (Throwable error) {
                    response.addProperty("ok", false);
                    response.addProperty("error", String.valueOf(error));
                    Throwable cause = error.getCause();
                    if (cause != null) {
                        response.addProperty("cause", String.valueOf(cause));
                    }
                }
                out.println(response);
            }
        } catch (Throwable error) {
            plugin.getLogger().warning("[Remote] connection " + ip + " error: " + error);
        } finally {
            plugin.getLogger().info("[Remote] disconnected " + ip);
        }
    }

    @Override
    public void close() {
        running = false;
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }
}
