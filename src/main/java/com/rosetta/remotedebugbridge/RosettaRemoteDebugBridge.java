/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraftforge.common.MinecraftForge
 *  net.minecraftforge.event.server.ServerStartingEvent
 *  net.minecraftforge.eventbus.api.SubscribeEvent
 *  net.minecraftforge.fml.common.Mod
 *  net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent
 *  net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent
 *  net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext
 *  org.apache.logging.log4j.LogManager
 *  org.apache.logging.log4j.Logger
 */
package com.rosetta.remotedebugbridge;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import com.rosetta.remotedebugbridge.eventbus.bus.RosettaEventBus;
import com.rosetta.remotedebugbridge.core.RosettaCore;
import com.rosetta.remotedebugbridge.core.ScriptType;
import com.rosetta.remotedebugbridge.logging.RosettaLogger;
import com.rosetta.remotedebugbridge.net.RemoteBridge;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(value="rosetta_remote_debug_bridge")
public class RosettaRemoteDebugBridge {
    public static final String MOD_ID = "rosetta_remote_debug_bridge";
    public static final Logger LOGGER = LogManager.getLogger();
    public static final RosettaEventBus EVENT_BUS = new RosettaEventBus();
    private static RosettaCore coreInstance;

    public RosettaRemoteDebugBridge() {
        LOGGER.info("RosettaRemoteDebugBridge initializing...");
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::commonSetup);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::clientSetup);
        MinecraftForge.EVENT_BUS.register((Object)this);
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

