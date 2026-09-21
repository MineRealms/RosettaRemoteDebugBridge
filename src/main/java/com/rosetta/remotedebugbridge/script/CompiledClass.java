package com.rosetta.remotedebugbridge.script;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class CompiledClass {
    public final String className;
    public final byte[] bytecode;
    public final Map<String, byte[]> allClasses;

    public CompiledClass(String className, byte[] bytecode) {
        this(className, bytecode, Collections.singletonMap(className, bytecode));
    }

    public CompiledClass(String className, byte[] bytecode, Map<String, byte[]> allClasses) {
        this.className = className;
        this.bytecode = bytecode;
        this.allClasses = allClasses != null ? new HashMap<String, byte[]>(allClasses) : new HashMap<String, byte[]>();
        if (bytecode != null) {
            this.allClasses.putIfAbsent(className, bytecode);
        }
    }
}
