/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.spongepowered.rain.asm.service.IClassBytecodeProvider
 */
package com.rosetta.remotedebugbridge.script.helper;

import java.lang.reflect.Field;
import com.rosetta.remotedebugbridge.RosettaRemoteDebugBridge;
import com.rosetta.remotedebugbridge.script.helper.BytecodeProviderWrapper;
import org.spongepowered.rain.asm.service.IClassBytecodeProvider;

class BytecodeProviderInstaller {
    BytecodeProviderInstaller() {
    }

    public static void install() throws Exception {
        RosettaRemoteDebugBridge.LOGGER.info("Installing BytecodeProvider wrapper...");
        Class<?> serviceClass = Class.forName("org.spongepowered.rain.asm.service.MixinService");
        Object service = serviceClass.getMethod("getService", new Class[0]).invoke(null, new Object[0]);
        Object currentProvider = service.getClass().getMethod("getBytecodeProvider", new Class[0]).invoke(service, new Object[0]);
        RosettaRemoteDebugBridge.LOGGER.info("Current provider: {}", (Object)currentProvider.getClass().getName());
        if (currentProvider instanceof BytecodeProviderWrapper) {
            RosettaRemoteDebugBridge.LOGGER.info("Already wrapped, skipping");
            return;
        }
        BytecodeProviderWrapper wrapper = new BytecodeProviderWrapper((IClassBytecodeProvider)currentProvider);
        boolean replaced = false;
        for (Class<?> current = service.getClass(); current != null && current != Object.class; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField("bytecodeProvider");
                field.setAccessible(true);
                field.set(service, wrapper);
                RosettaRemoteDebugBridge.LOGGER.info("\u2705 Replaced bytecodeProvider field in: {}", (Object)current.getName());
                replaced = true;
                break;
            }
            catch (NoSuchFieldException e) {
                continue;
            }
        }
        if (!replaced) {
            throw new RuntimeException("Could not find bytecodeProvider field in MixinService");
        }
    }
}

