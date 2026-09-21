/*
 * Decompiled with CFR 0.152.
 */
package com.rosetta.remotedebugbridge.script;

import java.io.File;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileAttribute;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import com.rosetta.remotedebugbridge.RosettaRemoteDebugBridge;
import com.rosetta.remotedebugbridge.script.CompiledClass;
import com.rosetta.remotedebugbridge.script.JavaSourceCompiler;
import com.rosetta.remotedebugbridge.utils.PathUtils;

public class ClassReplacementManager {
    private final JavaSourceCompiler compiler;
    private final Path replaceDirectory;
    private final Path replacementOutputPath;
    private final Map<String, byte[]> compiledReplacements = new HashMap<String, byte[]>();

    public ClassReplacementManager(JavaSourceCompiler compiler, Path baseDirectory) {
        this.compiler = compiler;
        this.replaceDirectory = baseDirectory.resolve("replace");
        Path gameDir = Paths.get(".", new String[0]).toAbsolutePath().normalize();
        this.replacementOutputPath = gameDir.resolve(".rosetta_remote_debug_bridge_replacements");
        if (!Files.exists(this.replaceDirectory, new LinkOption[0])) {
            try {
                Files.createDirectories(this.replaceDirectory, new FileAttribute[0]);
                RosettaRemoteDebugBridge.LOGGER.info("Created replace directory: {}", (Object)this.replaceDirectory);
            }
            catch (Exception e) {
                RosettaRemoteDebugBridge.LOGGER.error("Failed to create replace directory", (Throwable)e);
            }
        }
        if (!Files.exists(this.replacementOutputPath, new LinkOption[0])) {
            try {
                Files.createDirectories(this.replacementOutputPath, new FileAttribute[0]);
                RosettaRemoteDebugBridge.LOGGER.info("Created rosetta_remote_debug_bridge_replacements directory: {}", (Object)this.replacementOutputPath);
            }
            catch (Exception e) {
                RosettaRemoteDebugBridge.LOGGER.error("Failed to create rosetta_remote_debug_bridge_replacements directory", (Throwable)e);
            }
        }
    }

    public void processReplacements() {
        if (this.compiler == null) {
            RosettaRemoteDebugBridge.LOGGER.warn("Compiler not available, cannot process class replacements");
            return;
        }
        if (!Files.exists(this.replaceDirectory, new LinkOption[0])) {
            RosettaRemoteDebugBridge.LOGGER.debug("Replace directory does not exist: {}", (Object)this.replaceDirectory);
            return;
        }
        RosettaRemoteDebugBridge.LOGGER.warn("Class replacement output is compiled to {} but is NOT applied at runtime in this version (requires an agent/class-transformer).", (Object)this.replacementOutputPath);
        RosettaRemoteDebugBridge.LOGGER.info("========================================");
        RosettaRemoteDebugBridge.LOGGER.info("Processing Class Replacements (compilation only)");
        RosettaRemoteDebugBridge.LOGGER.info("========================================");
        List<Path> javaFiles = this.scanJavaFiles();
        if (javaFiles.isEmpty()) {
            RosettaRemoteDebugBridge.LOGGER.info("No replacement files found in {}", (Object)this.replaceDirectory);
            RosettaRemoteDebugBridge.LOGGER.info("========================================");
            return;
        }
        RosettaRemoteDebugBridge.LOGGER.info("Found {} replacement file(s)", (Object)javaFiles.size());
        for (Path file : javaFiles) {
            this.compileReplacement(file);
        }
        if (!this.compiledReplacements.isEmpty()) {
            this.applyReplacements();
        }
        RosettaRemoteDebugBridge.LOGGER.info("========================================");
        RosettaRemoteDebugBridge.LOGGER.info("Class Replacement Complete: {} classes compiled", (Object)this.compiledReplacements.size());
        RosettaRemoteDebugBridge.LOGGER.info("========================================");
    }

    private List<Path> scanJavaFiles() {
        ArrayList<Path> javaFiles = new ArrayList<Path>();
        try (Stream<Path> paths = Files.walk(this.replaceDirectory, new FileVisitOption[0]);){
            paths.filter(path -> path.toString().endsWith(".java")).forEach(javaFiles::add);
        }
        catch (Exception e) {
            RosettaRemoteDebugBridge.LOGGER.error("Failed to scan replace directory: {}", (Object)this.replaceDirectory, (Object)e);
        }
        return javaFiles;
    }

    private void compileReplacement(Path file) {
        try {
            RosettaRemoteDebugBridge.LOGGER.info("Compiling replacement: {}", (Object)file.getFileName());
            long startTime = System.currentTimeMillis();
            Path realFile = PathUtils.removeRosettaRemoteDebugBridgePrefix(file);
            CompiledClass compiled = this.compiler.compile(realFile);
            long compileTime = System.currentTimeMillis() - startTime;
            if (compiled.allClasses != null) {
                for (Map.Entry<String, byte[]> entry : compiled.allClasses.entrySet()) {
                    this.compiledReplacements.put(entry.getKey().replace('.', '/'), entry.getValue());
                }
            }
            String internalClassName = compiled.className.replace('.', '/');
            this.compiledReplacements.putIfAbsent(internalClassName, compiled.bytecode);
            RosettaRemoteDebugBridge.LOGGER.info("\u2713 Compiled: {} -> {} ({} bytes, {}ms)", (Object)file.getFileName(), (Object)internalClassName, (Object)compiled.bytecode.length, (Object)compileTime);
        }
        catch (Exception e) {
            RosettaRemoteDebugBridge.LOGGER.error("\u2717 Failed to compile: {}", (Object)file, (Object)e);
        }
    }

    private void applyReplacements() {
        try {
            int successCount = 0;
            for (Map.Entry<String, byte[]> entry : this.compiledReplacements.entrySet()) {
                String className = entry.getKey();
                byte[] bytecode = entry.getValue();
                String relativePath = className.replace("/", File.separator);
                Path classFilePath = this.replacementOutputPath.resolve(relativePath + ".class");
                Path parentDir = classFilePath.getParent();
                if (parentDir != null && !Files.exists(parentDir, new LinkOption[0])) {
                    Files.createDirectories(parentDir, new FileAttribute[0]);
                }
                Files.write(classFilePath, bytecode, new OpenOption[0]);
                RosettaRemoteDebugBridge.LOGGER.info("\u2713 Wrote replacement: {}", (Object)classFilePath);
                ++successCount;
            }
            RosettaRemoteDebugBridge.LOGGER.info("Successfully wrote {} replacement(s) to {}", (Object)successCount, (Object)this.replacementOutputPath);
            RosettaRemoteDebugBridge.LOGGER.warn("These .class files are not consumed by any loader in this version; applying class replacements requires an agent/class-transformer.");
        }
        catch (Exception e) {
            RosettaRemoteDebugBridge.LOGGER.error("Failed to write replacements to transformation directory", (Throwable)e);
        }
    }

    public Map<String, byte[]> getCompiledReplacements() {
        return new HashMap<String, byte[]>(this.compiledReplacements);
    }

    public boolean hasReplacements() {
        return !this.compiledReplacements.isEmpty();
    }

    public int getReplacementCount() {
        return this.compiledReplacements.size();
    }

    public Path getReplacementOutputPath() {
        return this.replacementOutputPath;
    }
}

