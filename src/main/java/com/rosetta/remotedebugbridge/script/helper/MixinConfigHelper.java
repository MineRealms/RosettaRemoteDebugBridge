/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.spongepowered.rain.asm.mixin.transformer.MixinConfig
 *  org.spongepowered.rain.asm.mixin.transformer.MixinProcessor
 *  org.spongepowered.rain.asm.mixin.transformer.MixinProcessorHolder
 *  org.spongepowered.rain.asm.mixin.transformer.PluginHandle
 *  org.spongepowered.rain.asm.service.IMixinService
 */
package com.rosetta.remotedebugbridge.script.helper;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.List;
import com.rosetta.remotedebugbridge.RosettaRemoteDebugBridge;
import org.spongepowered.rain.asm.mixin.transformer.MixinConfig;
import org.spongepowered.rain.asm.mixin.transformer.MixinProcessor;
import org.spongepowered.rain.asm.mixin.transformer.MixinProcessorHolder;
import org.spongepowered.rain.asm.mixin.transformer.PluginHandle;
import org.spongepowered.rain.asm.service.IMixinService;

public class MixinConfigHelper {
    public static void initializePlugin(MixinConfig config) {
        try {
            Field pluginField = MixinConfigHelper.findPluginField(config);
            if (pluginField == null) {
                throw new RuntimeException("Could not find plugin field in MixinConfig");
            }
            pluginField.setAccessible(true);
            Object existingPlugin = pluginField.get(config);
            if (existingPlugin != null) {
                RosettaRemoteDebugBridge.LOGGER.info("MixinConfig already has a plugin, skipping initialization");
                return;
            }
            PluginHandle plugin = MixinConfigHelper.getPluginFromExistingConfig();
            if (plugin == null) {
                plugin = MixinConfigHelper.createDefaultPlugin(config);
            }
            if (plugin != null) {
                pluginField.set(config, plugin);
                RosettaRemoteDebugBridge.LOGGER.info("\u2705 Successfully set plugin for MixinConfig: {}", (Object)config.getName());
            } else {
                RosettaRemoteDebugBridge.LOGGER.warn("\u26a0\ufe0f Could not create or find a plugin for MixinConfig");
            }
        }
        catch (Exception e) {
            RosettaRemoteDebugBridge.LOGGER.error("Failed to initialize plugin for MixinConfig", (Throwable)e);
            throw new RuntimeException("Failed to initialize MixinConfig plugin", e);
        }
    }

    private static Field findPluginField(MixinConfig config) {
        for (Class<?> current = config.getClass(); current != null && current != Object.class; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField("plugin");
                return field;
            }
            catch (NoSuchFieldException e) {
                continue;
            }
        }
        return null;
    }

    private static PluginHandle getPluginFromExistingConfig() {
        try {
            MixinConfig firstConfig;
            Field pluginField;
            MixinProcessor processor = MixinProcessorHolder.getInstance();
            if (processor == null) {
                return null;
            }
            Field configsField = processor.getClass().getDeclaredField("configs");
            configsField.setAccessible(true);
            List configs = (List)configsField.get(processor);
            if (!configs.isEmpty() && (pluginField = MixinConfigHelper.findPluginField(firstConfig = (MixinConfig)configs.get(0))) != null) {
                pluginField.setAccessible(true);
                PluginHandle plugin = (PluginHandle)pluginField.get(firstConfig);
                if (plugin != null) {
                    RosettaRemoteDebugBridge.LOGGER.info("Found existing plugin from config: {}", (Object)firstConfig.getName());
                    return plugin;
                }
            }
        }
        catch (Exception e) {
            RosettaRemoteDebugBridge.LOGGER.debug("Could not get plugin from existing config", (Throwable)e);
        }
        return null;
    }

    private static PluginHandle createDefaultPlugin(MixinConfig config) {
        try {
            Class<?> serviceClass = Class.forName("org.spongepowered.rain.asm.service.MixinService");
            Object service = serviceClass.getMethod("getService", new Class[0]).invoke(null, new Object[0]);
            if (service == null || !(service instanceof IMixinService)) {
                RosettaRemoteDebugBridge.LOGGER.error("MixinService is null, cannot create PluginHandle");
                return null;
            }
            IMixinService mixinService = (IMixinService)service;
            Constructor constructor = PluginHandle.class.getDeclaredConstructor(MixinConfig.class, IMixinService.class, String.class);
            constructor.setAccessible(true);
            PluginHandle handle = (PluginHandle)constructor.newInstance(config, mixinService, null);
            RosettaRemoteDebugBridge.LOGGER.info("\u2705 Created PluginHandle with null plugin (allows all mixins)");
            return handle;
        }
        catch (Exception e) {
            RosettaRemoteDebugBridge.LOGGER.error("Failed to create default PluginHandle\uff0c\u5c1d\u8bd5\u5907\u7528\u624b\u6bb5\uff01", (Throwable)e);
            try {
                Class<?> serviceClass = Class.forName("org.spongepowered.rain.asm.service.MixinService");
                Object service = serviceClass.getMethod("getService", new Class[0]).invoke(null, new Object[0]);
                if (service == null || !(service instanceof IMixinService)) {
                    RosettaRemoteDebugBridge.LOGGER.error("MixinService is null, cannot create PluginHandle");
                    return null;
                }
                IMixinService mixinService = (IMixinService)service;
                PluginHandle handle = new PluginHandle(config, mixinService, null);
                return handle;
            }
            catch (Exception err) {
                RosettaRemoteDebugBridge.LOGGER.error("Failed to create default PluginHandle", (Throwable)err);
                return null;
            }
        }
    }

    public static void fullyInitializeConfig(MixinConfig config) {
        MixinConfigHelper.initializePlugin(config);
    }
}

