/*
 * Decompiled with CFR 0.152.
 */
package com.rosetta.remotedebugbridge.utils;

import java.nio.file.Path;
import java.nio.file.Paths;

public class PathUtils {
    public static Path removeRosettaRemoteDebugBridgePrefix(Path originalPath) {
        int nameCount = originalPath.getNameCount();
        if (nameCount >= 2) {
            String first = originalPath.getName(0).toString();
            String second = originalPath.getName(1).toString();
            if (first.equals("RosettaRemoteDebugBridge") && second.equals("replace")) {
                return originalPath.subpath(2, nameCount);
            }
        }
        return originalPath;
    }

    public static Path removePathPrefix(Path originalPath, String prefixToRemove) {
        Path prefixPath = Paths.get(prefixToRemove, new String[0]);
        if (originalPath.startsWith(prefixPath)) {
            int skipCount = prefixPath.getNameCount();
            return originalPath.subpath(skipCount, originalPath.getNameCount());
        }
        return originalPath;
    }
}

