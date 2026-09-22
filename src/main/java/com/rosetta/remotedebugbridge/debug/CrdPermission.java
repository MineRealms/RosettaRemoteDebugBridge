package com.rosetta.remotedebugbridge.debug;

/**
 * Permission tiers for client remote debug. Higher rank implies the lower ones.
 */
public enum CrdPermission {
    READ(0),
    RELOAD(1),
    ACTION(2),
    SCRIPT(3);

    private final int rank;

    CrdPermission(int rank) {
        this.rank = rank;
    }

    public int rank() {
        return this.rank;
    }

    public boolean allows(CrdPermission required) {
        return this.rank >= required.rank;
    }

    public static CrdPermission parse(String value, CrdPermission fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return valueOf(value.trim().toUpperCase());
        }
        catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    public static CrdPermission min(CrdPermission a, CrdPermission b) {
        return a.rank <= b.rank ? a : b;
    }
}
