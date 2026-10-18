package com.rosetta.remotedebugbridge.platform;

import java.nio.file.Path;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Loader boundary. Everything that used to call FMLPaths / FMLEnvironment / Dist goes through here.
 */
public final class Platform {

    private Platform() {
    }

    public static Path gameDir() {
        return FabricLoader.getInstance().getGameDir();
    }

    public static boolean isClient() {
        return FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;
    }

    /**
     * "named" in a Loom development run, "intermediary" in a production Fabric instance.
     */
    public static String runtimeNamespace() {
        try {
            return FabricLoader.getInstance().getMappingResolver().getCurrentRuntimeNamespace();
        } catch (Throwable ignored) {
            return "named";
        }
    }

    public static boolean isRemappedRuntime() {
        return !"named".equals(runtimeNamespace());
    }
}
