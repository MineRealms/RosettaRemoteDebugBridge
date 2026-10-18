package com.rosetta.remotedebugbridge.core;

import com.rosetta.remotedebugbridge.RosettaRemoteDebugBridge;
import com.rosetta.remotedebugbridge.logging.RosettaLogger;
import com.rosetta.remotedebugbridge.logging.ScriptErrorCollector;
import com.rosetta.remotedebugbridge.platform.Platform;
import com.rosetta.remotedebugbridge.script.JavaScriptLoader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;

/**
 * Directory layout, script loaders and hot reload. Loader agnostic: the only platform
 * dependency is the game directory.
 */
public class RosettaCore {

    private static final String ROSETTA_FOLDER = "RosettaRemoteDebugBridge";

    private final Path rootPath;
    private final Path serverPath;
    private final Path clientPath;
    private final Path startupPath;
    private final Path assetsPath;
    private final Path dataPath;

    private final Map<ScriptType, RosettaLogger> loggers = new EnumMap<>(ScriptType.class);
    private final Map<ScriptType, JavaScriptLoader> loaders = new EnumMap<>(ScriptType.class);
    private final Map<ScriptType, Boolean> loadedFlags = new EnumMap<>(ScriptType.class);

    public RosettaCore() {
        Path gameDir = Platform.gameDir();
        this.rootPath = gameDir.resolve(ROSETTA_FOLDER);
        this.serverPath = this.rootPath.resolve("server");
        this.clientPath = this.rootPath.resolve("client");
        this.startupPath = this.rootPath.resolve("startup");
        this.assetsPath = this.rootPath.resolve("assets");
        this.dataPath = this.rootPath.resolve("data");
        for (ScriptType type : ScriptType.values()) {
            this.loggers.put(type, new RosettaLogger(type));
        }
        this.initializeFolders();
        for (ScriptType type : ScriptType.values()) {
            this.loaders.put(type, new JavaScriptLoader(type));
            this.loadedFlags.put(type, false);
        }
        RosettaRemoteDebugBridge.LOGGER.info("RosettaRemoteDebugBridge Core initialized at: {}", this.rootPath);
    }

    private void initializeFolders() {
        try {
            Files.createDirectories(this.serverPath);
            Files.createDirectories(this.clientPath);
            Files.createDirectories(this.startupPath);
            Files.createDirectories(this.dataPath);
            Files.createDirectories(this.assetsPath);
            this.createExampleFiles();
            RosettaRemoteDebugBridge.LOGGER.info("RosettaRemoteDebugBridge folder structure ready at: {}", this.rootPath);
        } catch (IOException error) {
            RosettaRemoteDebugBridge.LOGGER.error("Failed to create RosettaRemoteDebugBridge folder structure", error);
        }
    }

    private void createExampleFiles() {
        try {
            Path exampleFile = this.startupPath.resolve("Example.java");
            if (!Files.exists(exampleFile)) {
                String code = """
                        package rosetta.startup;

                        /**
                         * Example startup script, executed once when the game initialises.
                         */
                        public class Example {
                            public static void init() {
                                System.out.println("=================================");
                                System.out.println("Hello from RosettaRemoteDebugBridge startup script!");
                                System.out.println("=================================");
                            }
                        }
                        """;
                Files.writeString(exampleFile, code, StandardCharsets.UTF_8);
            }
            Path readme = this.rootPath.resolve("README.txt");
            if (!Files.exists(readme)) {
                String text = """
                        RosettaRemoteDebugBridge (Fabric 1.20.1)
                        =========================================

                        FOLDERS
                          startup/  runs once when the game initialises
                          server/   runs each time a server starts (integrated or dedicated)
                          client/   runs during client initialisation
                          assets/   resource pack root (optional)
                          data/     data pack root (optional)

                        SCRIPTS
                        Every script class must declare a public static init() method.
                        The package must match the folder:
                          startup -> package rosetta.startup;
                          server  -> package rosetta.server;
                          client  -> package rosetta.client;

                        REMOTE CONTROL PLANE
                        A newline-delimited JSON TCP server starts when a server starts.
                          bind 127.0.0.1:48790  (-Drosetta.remote.bind / -Drosetta.remote.port)
                          token RosettaRemoteDebugBridge/remote-token.txt (-Drosetta.remote.token)
                        Commands: ping, console, exec, reflect, upload, read, tail, ls, plugins.

                        HOT RELOAD
                          /java reload [startup|server|client]

                        NOTE
                        Dynamic mixins and CoreMods are not part of the Fabric build.
                        """;
                Files.writeString(readme, text, StandardCharsets.UTF_8);
            }
        } catch (IOException error) {
            RosettaRemoteDebugBridge.LOGGER.error("Failed to create example files", error);
        }
    }

    public void loadScripts(ScriptType type) {
        if (Boolean.TRUE.equals(this.loadedFlags.get(type))) {
            RosettaRemoteDebugBridge.LOGGER.debug("Scripts of type {} already loaded, skipping", type);
            return;
        }
        this.doLoad(type);
        this.loadedFlags.put(type, true);
    }

    private void doLoad(ScriptType type) {
        JavaScriptLoader loader = this.loaders.get(type);
        if (loader == null || !loader.isAvailable()) {
            this.loggers.get(type).warn("Compiler not available, skipping {} scripts", type);
            return;
        }
        loader.loadJavaScripts(this.getScriptPath(type));
    }

    public void reload(ScriptType type) {
        RosettaLogger logger = this.loggers.get(type);
        logger.info("==== Reloading {} scripts ====", type);
        ScriptErrorCollector.clear(type);
        JavaScriptLoader previous = this.loaders.get(type);
        if (previous != null && previous.getClassLoader() != null) {
            int removed = RosettaRemoteDebugBridge.EVENT_BUS.unregisterByClassLoader(previous.getClassLoader());
            if (removed > 0) {
                logger.info("Unregistered {} event listener(s) from previous {} script classes", removed, type);
            }
        }
        JavaScriptLoader loader = new JavaScriptLoader(type);
        this.loaders.put(type, loader);
        this.loadedFlags.put(type, true);
        if (!loader.isAvailable()) {
            logger.error("Compiler not available, cannot reload {} scripts", type);
            return;
        }
        loader.loadJavaScripts(this.getScriptPath(type));
        logger.info("==== {} scripts reloaded ====", type);
    }

    public Path getRootPath() {
        return this.rootPath;
    }

    public Path getScriptPath(ScriptType type) {
        return switch (type) {
            case SERVER -> this.serverPath;
            case CLIENT -> this.clientPath;
            case STARTUP -> this.startupPath;
        };
    }

    public Path getAssetsPath() {
        return this.assetsPath;
    }

    public Path getDataPath() {
        return this.dataPath;
    }

    public RosettaLogger getLogger(ScriptType type) {
        return this.loggers.get(type);
    }

    public JavaScriptLoader getLoader(ScriptType type) {
        return this.loaders.get(type);
    }
}
