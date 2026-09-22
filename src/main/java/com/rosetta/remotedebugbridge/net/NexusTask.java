package com.rosetta.remotedebugbridge.net;

/**
 * Contract for a remote `exec` payload.
 *
 * The remote bridge wraps the user supplied method body into a generated class that
 * implements this interface. The class is compiled at runtime by the embedded ECJ
 * compiler and loaded in a throw-away class loader, so the server does not need a JDK.
 *
 * Security: this interface only exists for authenticated remote debugging. Every request
 * must present the bridge token; the bridge never binds to a public interface by default.
 */
public interface NexusTask {

    /**
     * @param plugin the owning Bukkit plugin (usually "Mohist"); null on a pure Forge server
     * @param args   extra arguments supplied by the remote caller (never null)
     * @return any value; the bridge stringifies and truncates it for the reply
     */
    Object run(org.bukkit.plugin.Plugin plugin, Object[] args) throws Throwable;
}
