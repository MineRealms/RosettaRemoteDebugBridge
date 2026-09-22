package com.rosetta.remote;

public interface Task {

    Object run(org.bukkit.plugin.Plugin plugin, Object[] args) throws Throwable;
}
