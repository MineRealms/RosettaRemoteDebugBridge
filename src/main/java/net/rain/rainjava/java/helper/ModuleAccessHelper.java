/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.apache.logging.log4j.LogManager
 *  org.apache.logging.log4j.Logger
 */
package net.rain.rainjava.java.helper;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ModuleAccessHelper {
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
    private static final Logger LOGGER = LogManager.getLogger();

    public static void forceOpenPackage(Module fromModule, String packageName, Module toModule) {
        try {
            if (fromModule.isOpen(packageName, toModule)) {
                return;
            }
            Method implAddOpens = Module.class.getDeclaredMethod("implAddOpens", String.class, Module.class);
            implAddOpens.setAccessible(true);
            implAddOpens.invoke(fromModule, packageName, toModule);
        }
        catch (Exception e) {
            throw new RuntimeException("Failed to open package " + packageName + " from " + fromModule.getName() + " to " + toModule.getName(), e);
        }
    }

    public static void addModuleReads(Module fromModule, Module toModule) {
        try {
            if (fromModule.canRead(toModule)) {
                return;
            }
            Method implAddReads = Module.class.getDeclaredMethod("implAddReads", Module.class);
            implAddReads.setAccessible(true);
            implAddReads.invoke(fromModule, toModule);
        }
        catch (Exception e) {
            throw new RuntimeException("Failed to add reads from " + fromModule.getName() + " to " + toModule.getName(), e);
        }
    }

    public static void fixMixinAccess() {
        try {
            Module javaBaseModule = ClassLoader.class.getModule();
            Module mixinModule = null;
            for (Module module : ModuleLayer.boot().modules()) {
                if (module.getName() == null || !module.getName().contains("rain.mixin")) continue;
                mixinModule = module;
                break;
            }
            if (mixinModule == null) {
                Class<?> mixinConfigClass = Class.forName("org.spongepowered.rain.asm.mixin.transformer.MixinConfig");
                mixinModule = mixinConfigClass.getModule();
            }
            if (mixinModule != null && javaBaseModule != null) {
                String[] packagesToOpen;
                ModuleAccessHelper.addModuleReads(mixinModule, javaBaseModule);
                for (String pkg : packagesToOpen = new String[]{"java.lang", "java.lang.reflect", "java.util", "java.io", "java.nio", "java.net", "java.security", "sun.nio.ch", "sun.security.util", "jdk.internal.loader"}) {
                    ModuleAccessHelper.forceOpenPackage(javaBaseModule, pkg, mixinModule);
                }
                LOGGER.info("Successfully opened java.base packages to mixin module");
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
    }
}

