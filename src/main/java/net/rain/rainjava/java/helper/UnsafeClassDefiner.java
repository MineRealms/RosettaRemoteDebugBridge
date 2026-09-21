/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.apache.logging.log4j.LogManager
 *  org.apache.logging.log4j.Logger
 */
package net.rain.rainjava.java.helper;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import sun.misc.Unsafe;

public class UnsafeClassDefiner {
    private static final Logger LOGGER = LogManager.getLogger();
    private static Unsafe unsafe;
    private static MethodHandle defineClassHandle;
    private static boolean initialized;

    public static boolean initialize() {
        if (initialized) {
            return true;
        }
        return true;
    }

    private static MethodHandle createDefineClassHandle() {
        try {
            Class<MethodHandles.Lookup> lookupClass = MethodHandles.Lookup.class;
            Constructor constructor = lookupClass.getDeclaredConstructor(Class.class, Integer.TYPE);
            Field overrideField = AccessibleObject.class.getDeclaredField("override");
            long overrideOffset = unsafe.objectFieldOffset(overrideField);
            unsafe.putBoolean(constructor, overrideOffset, true);
            Object fullLookup = constructor.newInstance(ClassLoader.class, -1);
            LOGGER.info("[UnsafeClassDefiner]   \u2713 Created full-privilege Lookup");
            Method findVirtualMethod = lookupClass.getMethod("findVirtual", Class.class, String.class, MethodType.class);
            LOGGER.info("[UnsafeClassDefiner]   \u2713 Found defineClass via MethodHandle");
            return null;
        }
        catch (Exception e) {
            LOGGER.debug("[UnsafeClassDefiner] MethodHandle.Lookup approach failed: {}", (Object)e.getMessage());
            try {
                Method defineClassMethod = ClassLoader.class.getDeclaredMethod("defineClass", String.class, byte[].class, Integer.TYPE, Integer.TYPE);
                Field overrideField = AccessibleObject.class.getDeclaredField("override");
                long overrideOffset = unsafe.objectFieldOffset(overrideField);
                unsafe.putBoolean(defineClassMethod, overrideOffset, true);
                MethodHandles.Lookup lookup = MethodHandles.lookup();
                MethodHandle handle = lookup.unreflect(defineClassMethod);
                LOGGER.info("[UnsafeClassDefiner]   \u2713 Found defineClass via unreflect");
                return handle;
            }
            catch (Exception e2) {
                LOGGER.error("[UnsafeClassDefiner] All approaches failed", (Throwable)e2);
                e2.printStackTrace();
                return null;
            }
        }
    }

    public static Class<?> defineClass(ClassLoader loader, String className, byte[] bytecode) {
        if (!initialized && !UnsafeClassDefiner.initialize()) {
            throw new IllegalStateException("UnsafeClassDefiner not initialized");
        }
        try {
            LOGGER.info("[UnsafeClassDefiner] Defining class: {}", (Object)className);
            Class clazz = (Class)defineClassHandle.invoke(loader, className, bytecode, 0, bytecode.length);
            LOGGER.info("[UnsafeClassDefiner] \u2705 Successfully defined class: {}", (Object)className);
            return clazz;
        }
        catch (Throwable e) {
            Throwable cause = e.getCause();
            if (cause instanceof LinkageError || e instanceof LinkageError) {
                LOGGER.info("[UnsafeClassDefiner] Class {} already defined", (Object)className);
                try {
                    return loader.loadClass(className);
                }
                catch (ClassNotFoundException ex) {
                    throw new RuntimeException("Class already defined but cannot be loaded", ex);
                }
            }
            LOGGER.error("[UnsafeClassDefiner] Failed to define class: {}", (Object)className, (Object)e);
            throw new RuntimeException("Failed to define class: " + className, e);
        }
    }

    public static boolean verify() {
        if (!initialized && !UnsafeClassDefiner.initialize()) {
            return false;
        }
        try {
            byte[] testBytecode = UnsafeClassDefiner.generateTestClass();
            ClassLoader testLoader = UnsafeClassDefiner.class.getClassLoader();
            Class<?> testClass = UnsafeClassDefiner.defineClass(testLoader, "TestClass_" + System.currentTimeMillis(), testBytecode);
            LOGGER.info("[UnsafeClassDefiner] \u2705 Verification successful");
            return true;
        }
        catch (Exception e) {
            LOGGER.error("[UnsafeClassDefiner] Verification failed", (Throwable)e);
            return false;
        }
    }

    private static byte[] generateTestClass() {
        return new byte[]{-54, -2, -70, -66, 0, 0, 0, 55, 0, 4, 7, 0, 2, 1, 0, 9, 84, 101, 115, 116, 67, 108, 97, 115, 115, 7, 0, 3, 1, 0, 16, 106, 97, 118, 97, 47, 108, 97, 110, 103, 47, 79, 98, 106, 101, 99, 116, 0, 33, 0, 1, 0, 3, 0, 0, 0, 0, 0, 0, 0, 0};
    }

    static {
        initialized = false;
    }
}

