/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraftforge.fml.loading.FMLPaths
 *  org.apache.logging.log4j.LogManager
 *  org.apache.logging.log4j.Logger
 */
package com.rosetta.remotedebugbridge.logging;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraftforge.fml.loading.FMLPaths;
import com.rosetta.remotedebugbridge.core.ScriptType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class RosettaLogger {
    private static final Logger MAIN_LOGGER = LogManager.getLogger((String)"RosettaRemoteDebugBridge");
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Map<ScriptType, PrintWriter> WRITERS = new EnumMap<ScriptType, PrintWriter>(ScriptType.class);
    private static final AtomicBoolean initialized = new AtomicBoolean(false);
    private static final Map<ScriptType, RosettaLogger> INSTANCES = new EnumMap<ScriptType, RosettaLogger>(ScriptType.class);
    private final ScriptType scriptType;

    private static void ensureInitialized() {
        if (!initialized.compareAndSet(false, true)) {
            return;
        }
        RosettaLogger.initFiles();
    }

    private static void initFiles() {
        Path dir;
        try {
            dir = FMLPaths.GAMEDIR.get().resolve("logs").resolve("Rosetta");
        }
        catch (Exception e) {
            MAIN_LOGGER.error("[Rosetta] FMLPaths not ready yet, cannot initialize log files", (Throwable)e);
            initialized.set(false);
            return;
        }
        try {
            Files.createDirectories(dir, new FileAttribute[0]);
        }
        catch (IOException e) {
            MAIN_LOGGER.error("[Rosetta] Could not create logs/Java directory", (Throwable)e);
            return;
        }
        for (ScriptType type : ScriptType.values()) {
            Path file = dir.resolve(type.getName() + ".log");
            try {
                PrintWriter pw = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file.toFile(), false), StandardCharsets.UTF_8)), true);
                WRITERS.put(type, pw);
                pw.printf("[%s] [%s/INFO] RosettaRemoteDebugBridge logger started%n", LocalDateTime.now().format(TS), type.getName().toUpperCase());
                pw.flush();
            }
            catch (IOException e) {
                MAIN_LOGGER.error("[Rosetta] Failed to open log file: {}", (Object)file, (Object)e);
            }
        }
        MAIN_LOGGER.info("[Rosetta] Independent log files initialized in: {}", (Object)dir);
    }

    public static RosettaLogger of(ScriptType type) {
        return INSTANCES.computeIfAbsent(type, RosettaLogger::new);
    }

    public static void printInfo(ScriptType t, String msg, Object ... args) {
        RosettaLogger.write(t, Level.INFO, null, msg, args);
    }

    public static void printWarn(ScriptType t, String msg, Object ... args) {
        RosettaLogger.write(t, Level.WARN, null, msg, args);
    }

    public static void printError(ScriptType t, String msg, Object ... args) {
        RosettaLogger.write(t, Level.ERROR, null, msg, args);
    }

    public static void printDebug(ScriptType t, String msg, Object ... args) {
        RosettaLogger.write(t, Level.DEBUG, null, msg, args);
    }

    public static void printCompilerOutput(ScriptType type, String compilerOutput) {
        RosettaLogger.ensureInitialized();
        String timestamp = LocalDateTime.now().format(TS);
        String tag = type.getName().toUpperCase();
        PrintWriter pw = WRITERS.get((Object)type);
        if (pw != null) {
            pw.printf("[%s] [%s/ERROR] === Compiler Output ===%n", timestamp, tag);
            for (String line : compilerOutput.split("\n")) {
                pw.printf("[%s] [%s/ERROR] %s%n", timestamp, tag, line);
            }
            pw.printf("[%s] [%s/ERROR] === End of Compiler Output ===%n", timestamp, tag);
            pw.flush();
        }
        String firstLine = compilerOutput.lines().findFirst().orElse("(no output)");
        MAIN_LOGGER.error("[{}] Compilation failed: {}", (Object)tag, (Object)firstLine);
    }

    public static Path logFile(ScriptType type) {
        return FMLPaths.GAMEDIR.get().resolve("logs").resolve("Rosetta").resolve(type.getName() + ".log");
    }

    public static void closeAll() {
        WRITERS.values().forEach(PrintWriter::close);
        WRITERS.clear();
        initialized.set(false);
    }

    public RosettaLogger(ScriptType scriptType) {
        this.scriptType = scriptType;
    }

    public void info(String msg, Object ... args) {
        RosettaLogger.write(this.scriptType, Level.INFO, null, msg, args);
    }

    public void warn(String msg, Object ... args) {
        RosettaLogger.write(this.scriptType, Level.WARN, null, msg, args);
    }

    public void error(String msg, Object ... args) {
        RosettaLogger.write(this.scriptType, Level.ERROR, null, msg, args);
    }

    public void debug(String msg, Object ... args) {
        RosettaLogger.write(this.scriptType, Level.DEBUG, null, msg, args);
    }

    public void error(String msg, Throwable t, Object ... args) {
        RosettaLogger.write(this.scriptType, Level.ERROR, t, msg, args);
    }

    public ScriptType getScriptType() {
        return this.scriptType;
    }

    private static void write(ScriptType type, Level level, Throwable thrown, String msg, Object[] args) {
        RosettaLogger.ensureInitialized();
        String formatted = RosettaLogger.format(msg, args);
        String timestamp = LocalDateTime.now().format(TS);
        String tag = type.getName().toUpperCase();
        PrintWriter pw = WRITERS.get((Object)type);
        if (pw != null) {
            pw.printf("[%s] [%s/%s] %s%n", new Object[]{timestamp, tag, level, formatted});
            if (thrown != null) {
                thrown.printStackTrace(pw);
            }
            pw.flush();
        } else {
            MAIN_LOGGER.warn("[Rosetta] Independent log writer not available for type: {}", (Object)type);
        }
        String mainMsg = "[" + tag + "] " + formatted;
        switch (level) {
            case INFO: {
                MAIN_LOGGER.info(mainMsg);
                break;
            }
            case WARN: {
                MAIN_LOGGER.warn(mainMsg);
                break;
            }
            case ERROR: {
                if (thrown != null) {
                    MAIN_LOGGER.error(mainMsg, thrown);
                    break;
                }
                MAIN_LOGGER.error(mainMsg);
                break;
            }
            case DEBUG: {
                MAIN_LOGGER.debug(mainMsg);
            }
        }
    }

    private static String format(String pattern, Object[] args) {
        if (args == null || args.length == 0) {
            return pattern;
        }
        StringBuilder sb = new StringBuilder(pattern.length() + 64);
        int ai = 0;
        int i = 0;
        while (i < pattern.length()) {
            if (i + 1 < pattern.length() && pattern.charAt(i) == '{' && pattern.charAt(i + 1) == '}') {
                if (ai < args.length) {
                    Object arg;
                    if ((arg = args[ai++]) instanceof Throwable) {
                        Throwable th = (Throwable)arg;
                        StringWriter sw = new StringWriter();
                        th.printStackTrace(new PrintWriter(sw));
                        sb.append(sw);
                    } else {
                        sb.append(arg);
                    }
                } else {
                    sb.append("{}");
                }
                i += 2;
                continue;
            }
            sb.append(pattern.charAt(i++));
        }
        return sb.toString();
    }

    private static enum Level {
        INFO,
        WARN,
        ERROR,
        DEBUG;

    }
}

