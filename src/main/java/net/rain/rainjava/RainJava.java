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
package net.rain.rainjava;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.rain.eventbus.bus.RainEventBus;
import net.rain.rainjava.core.RainJavaCore;
import net.rain.rainjava.core.ScriptType;
import net.rain.rainjava.logging.RainJavaLogger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(value="rainjava")
public class RainJava {
    public static final String MOD_ID = "rainjava";
    public static final Logger LOGGER = LogManager.getLogger();
    public static final RainEventBus EVENT_BUS = new RainEventBus();
    private static RainJavaCore coreInstance;

    public RainJava() {
        LOGGER.info("RainJava initializing...");
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::commonSetup);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::clientSetup);
        MinecraftForge.EVENT_BUS.register((Object)this);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
    }

    private void clientSetup(FMLClientSetupEvent event) {
        RainJavaLogger logger = coreInstance.getLogger(ScriptType.CLIENT);
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
        RainJavaLogger logger = coreInstance.getLogger(ScriptType.SERVER);
        logger.info("Server starting - loading server scripts", new Object[0]);
        try {
            coreInstance.loadScripts(ScriptType.SERVER);
        }
        catch (Exception e) {
            logger.error("Failed to load server scripts: {}", e.getMessage(), e);
        }
    }

    public static RainJavaCore getCore() {
        return coreInstance;
    }

    static {
        RainJavaLogger.of(ScriptType.STARTUP).info("init", new Object[0]);
        coreInstance = new RainJavaCore();
        coreInstance.loadScripts(ScriptType.STARTUP);
    }
}

