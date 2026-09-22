package com.rosetta.remotedebugbridge.net;

/**
 * Script-facing Bukkit registration facade.
 *
 * Scripts (Rosetta server scripts, remote `exec` payloads, Coder scripts) should register
 * commands and listeners through these methods instead of touching Bukkit internals.
 * Every registration is tracked by the class loader of the supplied instance, so
 * {@code /java reload server} and {@link BukkitAdapter#cleanupClassLoader} can retire
 * exactly the artifacts of the previous script generation - no duplicate commands and
 * no duplicate listeners after a reload.
 *
 * All methods must be called on the server thread (or from bridge code that dispatches
 * onto it); the remote bridge does this automatically.
 */
public final class NexusBukkit {

    private NexusBukkit() {
    }

    /** Registers a Bukkit listener owned by the server owner plugin. Returns a status line. */
    public static String registerListener(Object listener) throws Exception {
        return BukkitAdapter.registerBukkitListener(listener);
    }

    /** Unregisters a single listener registered by {@link #registerListener}. */
    public static int unregisterListener(Object listener) {
        return BukkitAdapter.unregisterBukkitListener(listener);
    }

    /**
     * Registers a Bukkit command and tracks it by its class loader.
     *
     * @param fallbackPrefix namespace used when the label collides (e.g. "rosetta")
     * @param command        the command instance (usually an anonymous org.bukkit.command.Command)
     * @return a status line with the resolved label and tracking loader
     */
    public static String registerCommand(String fallbackPrefix, Object command) throws Exception {
        return BukkitAdapter.registerCommand(fallbackPrefix, command);
    }

    /** Unregisters a command previously registered through {@link #registerCommand}. */
    public static int unregisterCommand(Object command) {
        return BukkitAdapter.unregisterCommand(command);
    }

    /** Current adapter state (self-check instance/cancel counters, tracked listeners, owner). */
    public static String status() {
        return BukkitAdapter.describe();
    }
}
