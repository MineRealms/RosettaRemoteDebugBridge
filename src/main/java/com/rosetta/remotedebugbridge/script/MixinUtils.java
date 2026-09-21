/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.objectweb.asm.ClassReader
 *  org.objectweb.asm.ClassVisitor
 *  org.objectweb.asm.tree.ClassNode
 */
package com.rosetta.remotedebugbridge.script;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.tree.ClassNode;

public class MixinUtils {
    public static ClassNode createClassNode(byte[] bytecode) {
        ClassNode classNode = new ClassNode();
        ClassReader reader = new ClassReader(bytecode);
        reader.accept((ClassVisitor)classNode, 8);
        return classNode;
    }
}

