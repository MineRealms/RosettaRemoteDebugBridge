package com.rosetta.probe;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.bukkit.plugin.Plugin;

final class ProbeLog implements Closeable {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private final Plugin plugin;
    private final File file;
    private PrintWriter writer;

    ProbeLog(Plugin plugin) throws IOException {
        this.plugin = plugin;
        File dir = plugin.getDataFolder();
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("cannot create " + dir);
        }
        this.file = new File(dir, "portal-probe.log");
        this.writer = open(true);
    }

    private PrintWriter open(boolean append) throws IOException {
        return new PrintWriter(new OutputStreamWriter(new FileOutputStream(file, append), StandardCharsets.UTF_8), true);
    }

    void info(String message) {
        plugin.getLogger().info(message);
        write(message);
    }

    void warn(String message) {
        plugin.getLogger().warning(message);
        write("[WARN] " + message);
    }

    void clear() {
        try {
            writer.close();
            writer = open(false);
            info("[PortalProbe] log cleared");
        } catch (IOException error) {
            plugin.getLogger().warning("cannot clear log: " + error);
        }
    }

    private void write(String message) {
        writer.println("[" + LocalDateTime.now().format(FMT) + "] " + message);
    }

    String filePath() {
        return file.getAbsolutePath();
    }

    @Override
    public void close() {
        writer.close();
    }
}
