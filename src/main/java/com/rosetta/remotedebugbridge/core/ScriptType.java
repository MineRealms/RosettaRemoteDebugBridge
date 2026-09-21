/*
 * Decompiled with CFR 0.152.
 */
package com.rosetta.remotedebugbridge.core;

public enum ScriptType {
    SERVER("server"),
    CLIENT("client"),
    STARTUP("startup");

    private final String name;

    private ScriptType(String name) {
        this.name = name;
    }

    public String getName() {
        return this.name;
    }

    public static ScriptType fromName(String name) {
        for (ScriptType t : ScriptType.values()) {
            if (!t.getName().equalsIgnoreCase(name)) continue;
            return t;
        }
        return null;
    }

    public String toString() {
        return this.name;
    }
}

