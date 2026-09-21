/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.objectweb.asm.ClassReader
 *  org.objectweb.asm.ClassVisitor
 *  org.objectweb.asm.tree.ClassNode
 *  org.spongepowered.rain.asm.service.IClassBytecodeProvider
 */
package net.rain.rainjava.java.helper;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.rain.rainjava.RainJava;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.rain.asm.service.IClassBytecodeProvider;

public class BytecodeProviderWrapper
implements IClassBytecodeProvider {
    private final IClassBytecodeProvider delegate;
    private static final Map<String, byte[]> dynamicMixinBytecode = new ConcurrentHashMap<String, byte[]>();

    public BytecodeProviderWrapper(IClassBytecodeProvider delegate) {
        this.delegate = delegate;
        RainJava.LOGGER.info("\u2705 BytecodeProvider wrapped!");
    }

    public static void registerMixin(String className, byte[] bytecode) {
        dynamicMixinBytecode.put(className, bytecode);
        dynamicMixinBytecode.put(className.replace('.', '/'), bytecode);
        RainJava.LOGGER.info("Registered dynamic mixin bytecode: {}", (Object)className);
    }

    public static void clear() {
        dynamicMixinBytecode.clear();
    }

    public ClassNode getClassNode(String name) throws ClassNotFoundException, IOException {
        return this.getClassNode(name, true);
    }

    public ClassNode getClassNode(String name, boolean runTransformers) throws ClassNotFoundException, IOException {
        byte[] cachedBytecode = dynamicMixinBytecode.get(name);
        if (cachedBytecode == null) {
            cachedBytecode = dynamicMixinBytecode.get(name.replace('/', '.'));
        }
        if (cachedBytecode != null) {
            RainJava.LOGGER.info("\u2705 Providing cached bytecode for: {}", (Object)name);
            return this.bytesToClassNode(cachedBytecode);
        }
        return this.delegate.getClassNode(name, runTransformers);
    }

    private ClassNode bytesToClassNode(byte[] bytecode) {
        try {
            ClassNode node = new ClassNode();
            ClassReader reader = new ClassReader(bytecode);
            reader.accept((ClassVisitor)node, 8);
            return node;
        }
        catch (Exception e) {
            throw new RuntimeException("Failed to create ClassNode from bytecode", e);
        }
    }
}

