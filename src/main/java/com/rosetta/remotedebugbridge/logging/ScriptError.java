/*
 * Decompiled with CFR 0.152.
 */
package com.rosetta.remotedebugbridge.logging;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import com.rosetta.remotedebugbridge.core.ScriptType;

public class ScriptError {
    public final Type type;
    public final String message;
    public final String fileName;
    public final long lineNumber;
    public final long timestamp;
    public final List<String> stackTrace;
    public final ScriptType scriptType;

    public ScriptError(Type type, ScriptType scriptType, String message, String fileName, long lineNumber, List<String> stackTrace) {
        this.type = type;
        this.scriptType = scriptType;
        this.message = message;
        this.fileName = fileName;
        this.lineNumber = lineNumber;
        this.timestamp = System.currentTimeMillis();
        this.stackTrace = Collections.unmodifiableList(new ArrayList<String>(stackTrace));
    }

    public ScriptError(Type type, ScriptType scriptType, String message, String fileName, long lineNumber) {
        this(type, scriptType, message, fileName, lineNumber, List.of());
    }

    public ScriptError(Type type, ScriptType scriptType, String message, String fileName) {
        this(type, scriptType, message, fileName, -1L, List.of());
    }

    public static ScriptError fromThrowable(Type type, ScriptType scriptType, String fileName, Throwable t) {
        Throwable root = t;
        while ((root instanceof java.lang.reflect.InvocationTargetException || root instanceof ExceptionInInitializerError) && root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        List<String> lines = List.of(sw.toString().split("\n"));
        String msg = root.getMessage() != null ? root.getMessage() : root.getClass().getName();
        return new ScriptError(type, scriptType, msg, fileName, -1L, lines);
    }

    public String toString() {
        return "[" + String.valueOf((Object)this.type) + "] " + this.fileName + ":" + this.lineNumber + " - " + this.message;
    }

    public static enum Type {
        ERROR,
        WARN;

    }
}

