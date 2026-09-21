package net.rain.rainjava.java.helper;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import sun.misc.Unsafe;

public final class UnsafeClassDefiner {
    private static final Logger LOGGER = LogManager.getLogger();
    private static volatile MethodHandle defineClassHandle;
    private static volatile MethodHandles.Lookup trustedLookup;
    private static volatile boolean initializationFailed;

    private UnsafeClassDefiner() {
    }

    public static synchronized boolean initialize() {
        if (defineClassHandle != null) {
            return true;
        }
        if (initializationFailed) {
            return false;
        }
        try {
            Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            Unsafe unsafe = (Unsafe)unsafeField.get(null);
            Field implLookupField = MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP");
            MethodHandles.Lookup implLookup = (MethodHandles.Lookup)unsafe.getObject(unsafe.staticFieldBase(implLookupField), unsafe.staticFieldOffset(implLookupField));
            trustedLookup = MethodHandles.privateLookupIn(UnsafeClassDefiner.class, implLookup);
            defineClassHandle = implLookup.findVirtual(ClassLoader.class, "defineClass", MethodType.methodType(Class.class, String.class, byte[].class, Integer.TYPE, Integer.TYPE));
            LOGGER.info("[UnsafeClassDefiner] Ready (trusted Lookup + ClassLoader.defineClass)");
            return true;
        }
        catch (Throwable t) {
            initializationFailed = true;
            LOGGER.warn("[UnsafeClassDefiner] Not available on this JVM: {}", (Object)t.toString());
            return false;
        }
    }

    public static boolean isAvailable() {
        return UnsafeClassDefiner.initialize();
    }

    public static Class<?> defineClass(ClassLoader loader, String className, byte[] bytecode) {
        if (loader == null) {
            throw new IllegalArgumentException("loader must not be null");
        }
        if (className == null || className.isEmpty()) {
            throw new IllegalArgumentException("className must not be empty");
        }
        if (bytecode == null || bytecode.length == 0) {
            throw new IllegalArgumentException("bytecode must not be empty");
        }
        if (!UnsafeClassDefiner.initialize()) {
            try {
                return loader.loadClass(className);
            }
            catch (ClassNotFoundException e) {
                throw new IllegalStateException("UnsafeClassDefiner is not available on this JVM and the class is not loadable: " + className, e);
            }
        }
        try {
            return (Class<?>)defineClassHandle.invoke(loader, className, bytecode, 0, bytecode.length);
        }
        catch (Throwable t) {
            Throwable cause = t.getCause() != null ? t.getCause() : t;
            if (cause instanceof LinkageError) {
                try {
                    return loader.loadClass(className);
                }
                catch (ClassNotFoundException e) {
                    throw new RuntimeException("Class already defined but cannot be loaded: " + className, e);
                }
            }
            throw new RuntimeException("Failed to define class: " + className, cause);
        }
    }

    public static boolean verify() {
        if (!UnsafeClassDefiner.initialize()) {
            return false;
        }
        try {
            String binaryName = "rainjava.verify.Verify" + System.nanoTime();
            String internalName = binaryName.replace('.', '/');
            byte[] testBytecode = UnsafeClassDefiner.generateTestClass(internalName);
            ClassLoader loader = UnsafeClassDefiner.class.getClassLoader();
            Class<?> testClass = UnsafeClassDefiner.defineClass(loader, binaryName, testBytecode);
            LOGGER.info("[UnsafeClassDefiner] Verification successful: {}", (Object)testClass.getName());
            return true;
        }
        catch (Throwable t) {
            LOGGER.error("[UnsafeClassDefiner] Verification failed", t);
            return false;
        }
    }

    private static byte[] generateTestClass(String internalName) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(0xCAFEBABE);
            out.writeShort(0);
            out.writeShort(61);
            out.writeShort(5);
            out.writeByte(1);
            out.writeUTF(internalName);
            out.writeByte(7);
            out.writeShort(1);
            out.writeByte(1);
            out.writeUTF("java/lang/Object");
            out.writeByte(7);
            out.writeShort(3);
            out.writeShort(0x0021);
            out.writeShort(2);
            out.writeShort(4);
            out.writeShort(0);
            out.writeShort(0);
            out.writeShort(0);
            out.writeShort(0);
            out.flush();
            return bytes.toByteArray();
        }
        catch (IOException e) {
            throw new RuntimeException("Failed to generate test class bytecode", e);
        }
    }

    public static MethodHandles.Lookup trustedLookup() {
        return UnsafeClassDefiner.initialize() ? trustedLookup : MethodHandles.lookup();
    }
}
