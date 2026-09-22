package com.rosetta.remotedebugbridge.debug;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Client-side TOFU store: remembers server public key fingerprints per remote address.
 * File: &lt;gamedir&gt;/RosettaRemoteDebugBridge/client-known-servers.txt
 */
public final class ClientTrustStore {
    public enum Trust {
        NEW,
        MATCH,
        MISMATCH
    }

    private static final Logger LOGGER = LogManager.getLogger("RosettaRemoteDebugBridge/CRD");
    private static final String FILE_NAME = "client-known-servers.txt";
    private static final Map<String, String> KNOWN = new LinkedHashMap<String, String>();

    private ClientTrustStore() {
    }

    private static synchronized void ensureLoaded() {
        if (!KNOWN.isEmpty()) {
            return;
        }
        Path file = file();
        try {
            if (Files.exists(file)) {
                for (String raw : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    String line = raw.trim();
                    if (line.isEmpty() || line.startsWith("#") || !line.contains("=")) {
                        continue;
                    }
                    int split = line.indexOf('=');
                    KNOWN.put(line.substring(0, split).trim(), line.substring(split + 1).trim());
                }
            }
        }
        catch (IOException e) {
            LOGGER.warn("[CRD] failed to read trust store {}: {}", file, e.toString());
        }
    }

    private static Path file() {
        return FMLPaths.GAMEDIR.get().resolve("RosettaRemoteDebugBridge").resolve(FILE_NAME);
    }

    public static synchronized Trust check(String address, String fingerprint) {
        ensureLoaded();
        String known = KNOWN.get(normalize(address));
        if (known == null) {
            return Trust.NEW;
        }
        return known.equalsIgnoreCase(fingerprint) ? Trust.MATCH : Trust.MISMATCH;
    }

    public static synchronized void remember(String address, String fingerprint) {
        ensureLoaded();
        KNOWN.put(normalize(address), fingerprint);
        Path file = file();
        try {
            Files.createDirectories(file.getParent());
            StringBuilder sb = new StringBuilder("# RosettaRemoteDebugBridge client TOFU store (address=fingerprint)\n");
            for (Map.Entry<String, String> entry : KNOWN.entrySet()) {
                sb.append(entry.getKey()).append('=').append(entry.getValue()).append('\n');
            }
            Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
        }
        catch (IOException e) {
            LOGGER.warn("[CRD] failed to write trust store {}: {}", file, e.toString());
        }
    }

    public static synchronized Map<String, String> known() {
        ensureLoaded();
        return new LinkedHashMap<String, String>(KNOWN);
    }

    private static String normalize(String address) {
        return address == null || address.isBlank() ? "unknown" : address.trim().toLowerCase();
    }
}
