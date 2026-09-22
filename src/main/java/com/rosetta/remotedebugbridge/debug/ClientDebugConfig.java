package com.rosetta.remotedebugbridge.debug;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Client-side authorization config for Client Remote Debug (CRD).
 * File: &lt;gamedir&gt;/RosettaRemoteDebugBridge/client-debug.toml
 *
 * Defaults are the safe ones: remote debug fully disabled.
 */
public final class ClientDebugConfig {
    private static final Logger LOGGER = LogManager.getLogger("RosettaRemoteDebugBridge/CRD");
    private static final String FILE_NAME = "client-debug.toml";
    private static volatile ClientDebugConfig instance;

    private final Path file;
    private final boolean allowRemoteDebug;
    private final boolean requireConfirmPerSession;
    private final String maxPermission;
    private final int sessionTimeoutMinutes;

    private ClientDebugConfig(Path file, boolean allowRemoteDebug, boolean requireConfirmPerSession,
                             String maxPermission, int sessionTimeoutMinutes) {
        this.file = file;
        this.allowRemoteDebug = allowRemoteDebug;
        this.requireConfirmPerSession = requireConfirmPerSession;
        this.maxPermission = maxPermission;
        this.sessionTimeoutMinutes = sessionTimeoutMinutes;
    }

    public static ClientDebugConfig get() {
        ClientDebugConfig current = instance;
        if (current == null) {
            synchronized (ClientDebugConfig.class) {
                current = instance;
                if (current == null) {
                    current = load();
                    instance = current;
                }
            }
        }
        return current;
    }

    private static ClientDebugConfig load() {
        Path dir = FMLPaths.GAMEDIR.get().resolve("RosettaRemoteDebugBridge");
        Path file = dir.resolve(FILE_NAME);
        try {
            Files.createDirectories(dir);
            if (!Files.exists(file)) {
                Files.writeString(file, defaultContent(), StandardCharsets.UTF_8);
                LOGGER.info("[CRD] created default client config: {} (allowRemoteDebug=false)", file);
            }
        }
        catch (IOException e) {
            LOGGER.warn("[CRD] failed to create client config {}: {}", file, e.toString());
        }

        boolean allowRemoteDebug = false;
        boolean requireConfirmPerSession = true;
        String maxPermission = "RELOAD";
        int sessionTimeoutMinutes = 30;
        try {
            if (Files.exists(file)) {
                for (String raw : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    String line = raw.trim();
                    if (line.isEmpty() || line.startsWith("#") || !line.contains("=")) {
                        continue;
                    }
                    int split = line.indexOf('=');
                    String key = line.substring(0, split).trim();
                    String value = line.substring(split + 1).trim();
                    if (value.contains("#")) {
                        value = value.substring(0, value.indexOf('#')).trim();
                    }
                    if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                        value = value.substring(1, value.length() - 1);
                    }
                    switch (key) {
                        case "allowRemoteDebug":
                            allowRemoteDebug = Boolean.parseBoolean(value);
                            break;
                        case "requireConfirmPerSession":
                            requireConfirmPerSession = Boolean.parseBoolean(value);
                            break;
                        case "maxPermission":
                            maxPermission = normalizePermission(value);
                            break;
                        case "sessionTimeoutMinutes":
                            try {
                                sessionTimeoutMinutes = Math.max(1, Integer.parseInt(value));
                            }
                            catch (NumberFormatException ignored) {
                            }
                            break;
                        default:
                            break;
                    }
                }
            }
        }
        catch (IOException e) {
            LOGGER.warn("[CRD] failed to read client config {}: {}", file, e.toString());
        }
        return new ClientDebugConfig(file, allowRemoteDebug, requireConfirmPerSession, maxPermission, sessionTimeoutMinutes);
    }

    private static String normalizePermission(String value) {
        if (value == null) {
            return "READ";
        }
        String upper = value.trim().toUpperCase();
        return switch (upper) {
            case "READ", "RELOAD", "ACTION", "SCRIPT" -> upper;
            default -> {
                LOGGER.warn("[CRD] invalid maxPermission '{}' in client config; falling back to READ", value);
                yield "READ";
            }
        };
    }

    private static String defaultContent() {
        return """
                # RosettaRemoteDebugBridge - Client Remote Debug (CRD) authorization
                # This file is CLIENT-SIDE. Defaults are safe: nothing is allowed.
                #
                # allowRemoteDebug: master switch. When false the client always refuses
                #                    debug sessions (the server only sees "unauthorized").
                # requireConfirmPerSession: ask the player for every new session (C3+).
                # maxPermission: READ | RELOAD | ACTION | SCRIPT (upper bound the client grants).
                # sessionTimeoutMinutes: sessions expire after this idle period (C3+).
                #
                # Nothing here is persistent or hidden: no session can be opened without
                # the player's explicit consent once confirmation UI lands (C3).

                allowRemoteDebug = false
                requireConfirmPerSession = true
                maxPermission = "RELOAD"
                sessionTimeoutMinutes = 30
                """;
    }

    public Path getFile() {
        return this.file;
    }

    public boolean isAllowRemoteDebug() {
        return this.allowRemoteDebug;
    }

    public boolean isRequireConfirmPerSession() {
        return this.requireConfirmPerSession;
    }

    public String getMaxPermission() {
        return this.maxPermission;
    }

    public CrdPermission getMaxPermissionValue() {
        return CrdPermission.parse(this.maxPermission, CrdPermission.RELOAD);
    }

    public int getSessionTimeoutMinutes() {
        return this.sessionTimeoutMinutes;
    }
}
