/*
 * Decompiled with CFR 0.152.
 */
package com.rosetta.remotedebugbridge.script;

import java.util.HashMap;
import java.util.Map;

public class DynamicClassLoader
extends ClassLoader {
    private final Map<String, byte[]> classBytes = new HashMap<String, byte[]>();

    public DynamicClassLoader(ClassLoader parent) {
        super(parent);
    }

    public void addCompiledClass(String className, byte[] bytecode) {
        this.classBytes.put(className, bytecode);
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        byte[] bytecode = this.classBytes.get(name);
        if (bytecode != null) {
            return this.defineClass(name, bytecode, 0, bytecode.length);
        }
        return super.findClass(name);
    }
}

