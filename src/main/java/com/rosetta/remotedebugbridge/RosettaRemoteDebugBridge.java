/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.neoforged.neoforge.common.NeoForge
 *  net.neoforged.neoforge.event.server.ServerStartingEvent
 *  net.neoforged.bus.api.SubscribeEvent
 *  net.neoforged.fml.common.Mod
 *  net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
 *  net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent
 *  net.neoforged.fml.javafmlmod.FMLJavaModLoadingContext
 *  org.apache.logging.log4j.LogManager
 *  org.apache.logging.log4j.Logger
 */
package com.rosetta.remotedebugbridge;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import com.rosetta.remotedebugbridge.debug.ClientDebugAgent;
import com.rosetta.remotedebugbridge.debug.ClientSessionManager;
import com.rosetta.remotedebugbridge.eventbus.bus.RosettaEventBus;
import com.rosetta.remotedebugbridge.core.RosettaCore;
import com.rosetta.remotedebugbridge.core.ScriptType;
import com.rosetta.remotedebugbridge.logging.RosettaLogger;
import com.rosetta.remotedebugbridge.net.MaterialFallbackFix;
import com.rosetta.remotedebugbridge.net.RemoteBridge;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(value="rosetta_remote_debug_bridge")
public class RosettaRemoteDebugBridge {
    public static final String MOD_ID = "rosetta_remote_debug_bridge";
    public static final Logger LOGGER = LogManager.getLogger();
    public static final RosettaEventBus EVENT_BUS = new RosettaEventBus();
    public static IEventBus MOD_EVENT_BUS;
    private static RosettaCore coreInstance;

    public RosettaRemoteDebugBridge(IEventBus modEventBus) {
        LOGGER.info("RosettaRemoteDebugBridge initializing...");
        MOD_EVENT_BUS = modEventBus;
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::clientSetup);
        NeoForge.EVENT_BUS.register((Object)this);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
    }

    private void clientSetup(FMLClientSetupEvent event) {
        RosettaLogger logger = coreInstance.getLogger(ScriptType.CLIENT);
        logger.info("Client setup - loading client scripts", new Object[0]);
        try {
            coreInstance.loadScripts(ScriptType.CLIENT);
        }
        catch (Exception e) {
            logger.error("Failed to load client scripts: {}", e.getMessage(), e);
        }
        try {
            ClientDebugAgent.onClientSetup();
            com.rosetta.remotedebugbridge.debug.client.ClientDebugClientBridge.install();
        }
        catch (Throwable t) {
            LOGGER.warn("Client remote debug setup failed: {}", t.toString());
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        try {
            if (event.getEntity() instanceof ServerPlayer player) {
                ClientSessionManager.remove(player);
                com.rosetta.remotedebugbridge.debug.ServerSessions.onPlayerDisconnect(player);
            }
        }
        catch (Throwable t) {
            LOGGER.warn("Client remote debug logout cleanup failed: {}", t.toString());
        }
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        RosettaLogger logger = coreInstance.getLogger(ScriptType.SERVER);
        logger.info("Server starting - loading server scripts", new Object[0]);
        try {
            coreInstance.loadScripts(ScriptType.SERVER);
        }
        catch (Exception e) {
            logger.error("Failed to load server scripts: {}", e.getMessage(), e);
        }
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        MaterialFallbackFix.applyIfEnabled();
        try {
            RemoteBridge.start(event.getServer());
        }
        catch (Throwable t) {
            LOGGER.error("Failed to start RosettaNexus remote bridge (server keeps running)", t);
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        try {
            RemoteBridge.stop();
        }
        catch (Throwable t) {
            LOGGER.warn("Failed to stop RosettaNexus remote bridge: {}", t.toString());
        }
    }

    public static RosettaCore getCore() {
        return coreInstance;
    }

    static {
        RosettaLogger.of(ScriptType.STARTUP).info("init", new Object[0]);
        coreInstance = new RosettaCore();
        coreInstance.loadScripts(ScriptType.STARTUP);
    }
}

