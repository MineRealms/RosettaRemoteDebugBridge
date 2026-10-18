package com.rosetta.remotedebugbridge;

import com.rosetta.remotedebugbridge.core.RosettaCore;
import com.rosetta.remotedebugbridge.core.ScriptType;
import com.rosetta.remotedebugbridge.eventbus.bus.RosettaEventBus;
import com.rosetta.remotedebugbridge.logging.RosettaLogger;
import com.rosetta.remotedebugbridge.net.RemoteBridge;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class RosettaRemoteDebugBridge implements ModInitializer {

    public static final String MOD_ID = "rosetta_remote_debug_bridge";
    public static final Logger LOGGER = LogManager.getLogger("RosettaRemoteDebugBridge");
    public static final RosettaEventBus EVENT_BUS = new RosettaEventBus();

    private static RosettaCore coreInstance;
    private static volatile net.minecraft.server.MinecraftServer server;

    @Override
    public void onInitialize() {
        LOGGER.info("RosettaRemoteDebugBridge (Fabric) initializing...");

        RosettaLogger.of(ScriptType.STARTUP).info("init");
        coreInstance = new RosettaCore();
        coreInstance.loadScripts(ScriptType.STARTUP);

        com.rosetta.remotedebugbridge.command.RosettaCommands.register();

        ServerLifecycleEvents.SERVER_STARTED.register(started -> {
            server = started;
            RosettaLogger logger = coreInstance.getLogger(ScriptType.SERVER);
            logger.info("Server started - loading server scripts");
            try {
                coreInstance.loadScripts(ScriptType.SERVER);
            } catch (Exception error) {
                logger.error("Failed to load server scripts: {}", error.getMessage(), error);
            }
            try {
                RemoteBridge.start(started);
            } catch (Throwable error) {
                LOGGER.error("Failed to start RosettaNexus remote bridge (server keeps running)", error);
            }
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(stopping -> {
            server = null;
            try {
                RemoteBridge.stop();
            } catch (Throwable error) {
                LOGGER.warn("Failed to stop RosettaNexus remote bridge: {}", error.toString());
            }
        });
    }

    public static RosettaCore getCore() {
        return coreInstance;
    }

    /**
     * The running server (integrated or dedicated), or null outside a server lifetime.
     * Scripts and exec snippets run on the server thread, so they can use this directly.
     */
    public static net.minecraft.server.MinecraftServer getServer() {
        return server;
    }
}
