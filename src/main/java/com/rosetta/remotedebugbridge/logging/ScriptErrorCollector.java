/*
 * Decompiled with CFR 0.152.
 */
package com.rosetta.remotedebugbridge.logging;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import com.rosetta.remotedebugbridge.core.ScriptType;
import com.rosetta.remotedebugbridge.logging.RosettaLogger;
import com.rosetta.remotedebugbridge.logging.ScriptError;

public class ScriptErrorCollector {
    private static final Map<ScriptType, List<ScriptError>> ERRORS = new EnumMap<ScriptType, List<ScriptError>>(ScriptType.class);
    private static final Map<ScriptType, List<ScriptError>> WARNINGS = new EnumMap<ScriptType, List<ScriptError>>(ScriptType.class);

    private ScriptErrorCollector() {
    }

    public static void addError(ScriptError e) {
        if (e.type == ScriptError.Type.ERROR) {
            ERRORS.get((Object)e.scriptType).add(e);
        } else {
            WARNINGS.get((Object)e.scriptType).add(e);
        }
        RosettaLogger.printError(e.scriptType, "Script error in {}: {}", e.fileName, e.message);
    }

    public static void addError(ScriptType type, String message, String file, long line) {
        ScriptErrorCollector.addError(new ScriptError(ScriptError.Type.ERROR, type, message, file, line));
    }

    public static void addWarning(ScriptType type, String message, String file, long line) {
        ScriptErrorCollector.addError(new ScriptError(ScriptError.Type.WARN, type, message, file, line));
    }

    public static void addFromThrowable(ScriptType type, String file, Throwable t) {
        ScriptErrorCollector.addError(ScriptError.fromThrowable(ScriptError.Type.ERROR, type, file, t));
    }

    public static void clear(ScriptType type) {
        ERRORS.get((Object)type).clear();
        WARNINGS.get((Object)type).clear();
    }

    public static List<ScriptError> getErrors(ScriptType type) {
        return Collections.unmodifiableList(ERRORS.get((Object)type));
    }

    public static List<ScriptError> getWarnings(ScriptType type) {
        return Collections.unmodifiableList(WARNINGS.get((Object)type));
    }

    public static boolean hasErrors(ScriptType type) {
        return !ERRORS.get((Object)type).isEmpty();
    }

    public static boolean hasAnyErrors() {
        return ERRORS.values().stream().anyMatch(l -> !l.isEmpty());
    }

    public static boolean hasAnyIssues() {
        return ERRORS.values().stream().anyMatch(l -> !l.isEmpty()) || WARNINGS.values().stream().anyMatch(l -> !l.isEmpty());
    }

    public static ScriptType firstTypeWithErrors() {
        for (ScriptType t : ScriptType.values()) {
            if (ERRORS.get((Object)t).isEmpty()) continue;
            return t;
        }
        return null;
    }

    static {
        for (ScriptType t : ScriptType.values()) {
            ERRORS.put(t, new CopyOnWriteArrayList());
            WARNINGS.put(t, new CopyOnWriteArrayList());
        }
    }
}

