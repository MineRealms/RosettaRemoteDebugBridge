/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.apache.logging.log4j.LogManager
 *  org.apache.logging.log4j.Logger
 *  org.spongepowered.rain.asm.mixin.MixinEnvironment
 *  org.spongepowered.rain.asm.mixin.transformer.MixinConfig
 *  org.spongepowered.rain.asm.service.IClassTracker
 *  org.spongepowered.rain.asm.service.IMixinService
 *  org.spongepowered.rain.asm.service.MixinService
 */
package net.rain.rainjava.java.helper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.rain.asm.mixin.MixinEnvironment;
import org.spongepowered.rain.asm.mixin.transformer.MixinConfig;
import org.spongepowered.rain.asm.service.IClassTracker;
import org.spongepowered.rain.asm.service.IMixinService;
import org.spongepowered.rain.asm.service.MixinService;

public class MixinServiceHelper {
    private static final Logger LOGGER = LogManager.getLogger();
    private static boolean serviceFixed = false;
    private static final Object LOCK = new Object();

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public static boolean fixMixinService() {
        Object object = LOCK;
        synchronized (object) {
            if (serviceFixed) {
                return true;
            }
            try {
                LOGGER.info("========================================");
                LOGGER.info("[MixinServiceHelper] Fixing Mixin Service");
                LOGGER.info("========================================");
                IMixinService service = MixinService.getService();
                if (service == null) {
                    LOGGER.error("[MixinServiceHelper] \u274c MixinService.getService() returned null!");
                    return false;
                }
                LOGGER.info("[MixinServiceHelper] \u2705 MixinService: {}", (Object)service.getClass().getName());
                MixinEnvironment env = MixinEnvironment.getCurrentEnvironment();
                if (env == null) {
                    LOGGER.warn("[MixinServiceHelper] \u26a0\ufe0f MixinEnvironment is null, trying default...");
                    env = MixinEnvironment.getDefaultEnvironment();
                    if (env == null) {
                        LOGGER.error("[MixinServiceHelper] \u274c Cannot get MixinEnvironment");
                        return false;
                    }
                }
                LOGGER.info("[MixinServiceHelper] \u2705 MixinEnvironment: Phase={}", (Object)env.getPhase());
                MixinServiceHelper.forceInitializeBytecodeProvider(service);
                if (!MixinServiceHelper.verifyBytecodeProvider(service)) {
                    LOGGER.warn("[MixinServiceHelper] \u26a0\ufe0f BytecodeProvider verification failed, but continuing...");
                }
                try {
                    IClassTracker classTracker = service.getClassTracker();
                    if (classTracker != null) {
                        LOGGER.info("[MixinServiceHelper] \u2705 ClassTracker: {}", (Object)classTracker.getClass().getName());
                    } else {
                        LOGGER.warn("[MixinServiceHelper] \u26a0\ufe0f ClassTracker is null");
                    }
                }
                catch (Exception e) {
                    LOGGER.warn("[MixinServiceHelper] \u26a0\ufe0f ClassTracker error: {}", (Object)e.getMessage());
                }
                serviceFixed = true;
                LOGGER.info("[MixinServiceHelper] \u2705 Mixin Service fully initialized and verified");
                LOGGER.info("========================================");
                return true;
            }
            catch (Exception e) {
                LOGGER.error("[MixinServiceHelper] \u274c Failed to fix Mixin service", (Throwable)e);
                return false;
            }
        }
    }

    private static void forceInitializeBytecodeProvider(IMixinService service) {
        try {
            Method forceInitMethod = service.getClass().getMethod("forceInitializeBytecodeProvider", new Class[0]);
            forceInitMethod.invoke(service, new Object[0]);
            LOGGER.info("[MixinServiceHelper] \u2705 Called forceInitializeBytecodeProvider()");
        }
        catch (NoSuchMethodException e) {
            LOGGER.debug("[MixinServiceHelper] forceInitializeBytecodeProvider() not available");
        }
        catch (Exception e) {
            LOGGER.warn("[MixinServiceHelper] Failed to force initialize: {}", (Object)e.getMessage());
        }
    }

    private static boolean verifyBytecodeProvider(IMixinService service) {
        try {
            Method getBytecodeProvider = service.getClass().getMethod("getBytecodeProvider", new Class[0]);
            Object provider = getBytecodeProvider.invoke(service, new Object[0]);
            if (provider != null) {
                LOGGER.info("[MixinServiceHelper] \u2705 BytecodeProvider available: {}", (Object)provider.getClass().getName());
                if (provider.getClass().getName().contains("Fallback")) {
                    LOGGER.warn("[MixinServiceHelper] \u26a0\ufe0f Using Fallback BytecodeProvider");
                    return false;
                }
                return true;
            }
            LOGGER.error("[MixinServiceHelper] \u274c BytecodeProvider is null");
            return false;
        }
        catch (IllegalStateException e) {
            LOGGER.error("[MixinServiceHelper] \u274c Service not initialized: {}", (Object)e.getMessage());
            return false;
        }
        catch (Exception e) {
            LOGGER.error("[MixinServiceHelper] \u274c Cannot verify BytecodeProvider: {}", (Object)e.getMessage());
            return false;
        }
    }

    public static void fixConfigService(MixinConfig config) {
        if (config == null) {
            LOGGER.warn("[MixinServiceHelper] \u26a0\ufe0f Config is null, cannot fix");
            return;
        }
        try {
            IMixinService service = MixinService.getService();
            if (service == null) {
                LOGGER.error("[MixinServiceHelper] \u274c Cannot fix config: MixinService is null");
                return;
            }
            Class<?> configClass = config.getClass();
            Field serviceField = null;
            Class<?> currentClass = configClass;
            while (currentClass != null && serviceField == null) {
                try {
                    serviceField = currentClass.getDeclaredField("service");
                }
                catch (NoSuchFieldException e) {
                    currentClass = currentClass.getSuperclass();
                }
            }
            if (serviceField == null) {
                LOGGER.debug("[MixinServiceHelper] No 'service' field found in Config class hierarchy");
                return;
            }
            serviceField.setAccessible(true);
            Object currentService = serviceField.get(config);
            if (currentService == null) {
                LOGGER.warn("[MixinServiceHelper] \u26a0\ufe0f Config.service is NULL, injecting...");
                serviceField.set(config, service);
                LOGGER.info("[MixinServiceHelper] \u2705 Injected service into Config instance");
            } else {
                LOGGER.debug("[MixinServiceHelper] Config.service already set");
            }
        }
        catch (Exception e) {
            LOGGER.warn("[MixinServiceHelper] Cannot fix Config service: {}", (Object)e.getMessage());
        }
    }

    public static boolean verifyService() {
        try {
            IMixinService service = MixinService.getService();
            if (service == null) {
                LOGGER.error("[MixinServiceHelper] \u274c Service verification failed: service is null");
                return false;
            }
            MixinEnvironment env = MixinEnvironment.getCurrentEnvironment();
            if (env == null) {
                LOGGER.error("[MixinServiceHelper] \u274c Service verification failed: environment is null");
                return false;
            }
            try {
                Method getBytecodeProvider = service.getClass().getMethod("getBytecodeProvider", new Class[0]);
                Object provider = getBytecodeProvider.invoke(service, new Object[0]);
                if (provider == null) {
                    LOGGER.error("[MixinServiceHelper] \u274c BytecodeProvider is null");
                    return false;
                }
                LOGGER.info("[MixinServiceHelper] \u2705 BytecodeProvider available: {}", (Object)provider.getClass().getSimpleName());
            }
            catch (IllegalStateException e) {
                LOGGER.error("[MixinServiceHelper] \u274c Service not fully initialized: {}", (Object)e.getMessage());
                return false;
            }
            catch (Exception e) {
                LOGGER.error("[MixinServiceHelper] \u274c Cannot verify BytecodeProvider: {}", (Object)e.getMessage());
                return false;
            }
            LOGGER.info("[MixinServiceHelper] \u2705 Service verification passed");
            return true;
        }
        catch (Exception e) {
            LOGGER.error("[MixinServiceHelper] \u274c Service verification failed", (Throwable)e);
            return false;
        }
    }

    public static void diagnoseService() {
        block12: {
            LOGGER.info("========================================");
            LOGGER.info("[MixinServiceHelper] Service Diagnosis");
            LOGGER.info("========================================");
            try {
                IMixinService service = MixinService.getService();
                if (service != null) {
                    LOGGER.info("[MixinServiceHelper] \u2705 MixinService: {}", (Object)service.getClass().getName());
                    try {
                        Method getBytecodeProvider = service.getClass().getMethod("getBytecodeProvider", new Class[0]);
                        Object provider = getBytecodeProvider.invoke(service, new Object[0]);
                        LOGGER.info("[MixinServiceHelper] {} BytecodeProvider: {}", (Object)(provider != null ? "\u2705" : "\u274c"), (Object)(provider != null ? provider.getClass().getName() : "null"));
                    }
                    catch (IllegalStateException e) {
                        LOGGER.warn("[MixinServiceHelper] \u274c BytecodeProvider not ready: {}", (Object)e.getMessage());
                    }
                    catch (Exception e) {
                        LOGGER.warn("[MixinServiceHelper] \u274c BytecodeProvider error: {}", (Object)e.getMessage());
                    }
                    try {
                        IClassTracker tracker = service.getClassTracker();
                        LOGGER.info("[MixinServiceHelper] {} ClassTracker: {}", (Object)(tracker != null ? "\u2705" : "\u274c"), (Object)(tracker != null ? tracker.getClass().getName() : "null"));
                    }
                    catch (Exception e) {
                        LOGGER.warn("[MixinServiceHelper] \u274c ClassTracker error: {}", (Object)e.getMessage());
                    }
                    break block12;
                }
                LOGGER.error("[MixinServiceHelper] \u274c MixinService is NULL");
            }
            catch (Exception e) {
                LOGGER.error("[MixinServiceHelper] \u274c MixinService error: {}", (Object)e.getMessage());
            }
        }
        try {
            MixinEnvironment env = MixinEnvironment.getCurrentEnvironment();
            if (env != null) {
                LOGGER.info("[MixinServiceHelper] \u2705 MixinEnvironment: Phase={}, Side={}", (Object)env.getPhase(), (Object)env.getSide());
            } else {
                LOGGER.error("[MixinServiceHelper] \u274c MixinEnvironment is NULL");
            }
        }
        catch (Exception e) {
            LOGGER.error("[MixinServiceHelper] \u274c MixinEnvironment error: {}", (Object)e.getMessage());
        }
        LOGGER.info("========================================");
    }

    public static void reset() {
        serviceFixed = false;
    }
}

