/*
 * Decompiled with CFR 0.152.
 */
package net.rain.rainjava.java;

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
import net.rain.rainjava.RainJava;
import net.rain.rainjava.java.CompiledClass;
import net.rain.rainjava.java.JavaSourceCompiler;
import net.rain.rainjava.utils.PathUtils;

public class ClassReplacementManager {
    private final JavaSourceCompiler compiler;
    private final Path replaceDirectory;
    private final Path replacementOutputPath;
    private final Map<String, byte[]> compiledReplacements = new HashMap<String, byte[]>();

    public ClassReplacementManager(JavaSourceCompiler compiler, Path baseDirectory) {
        this.compiler = compiler;
        this.replaceDirectory = baseDirectory.resolve("replace");
        Path gameDir = Paths.get(".", new String[0]).toAbsolutePath().normalize();
        this.replacementOutputPath = gameDir.resolve(".rainjava_replacements");
        if (!Files.exists(this.replaceDirectory, new LinkOption[0])) {
            try {
                Files.createDirectories(this.replaceDirectory, new FileAttribute[0]);
                RainJava.LOGGER.info("Created replace directory: {}", (Object)this.replaceDirectory);
            }
            catch (Exception e) {
                RainJava.LOGGER.error("Failed to create replace directory", (Throwable)e);
            }
        }
        if (!Files.exists(this.replacementOutputPath, new LinkOption[0])) {
            try {
                Files.createDirectories(this.replacementOutputPath, new FileAttribute[0]);
                RainJava.LOGGER.info("Created rainjava_replacements directory: {}", (Object)this.replacementOutputPath);
            }
            catch (Exception e) {
                RainJava.LOGGER.error("Failed to create rainjava_replacements directory", (Throwable)e);
            }
        }
    }

    public void processReplacements() {
        if (this.compiler == null) {
            RainJava.LOGGER.warn("Compiler not available, cannot process class replacements");
            return;
        }
        if (!Files.exists(this.replaceDirectory, new LinkOption[0])) {
            RainJava.LOGGER.debug("Replace directory does not exist: {}", (Object)this.replaceDirectory);
            return;
        }
        RainJava.LOGGER.info("========================================");
        RainJava.LOGGER.info("Processing Class Replacements");
        RainJava.LOGGER.info("========================================");
        List<Path> javaFiles = this.scanJavaFiles();
        if (javaFiles.isEmpty()) {
            RainJava.LOGGER.info("No replacement files found in {}", (Object)this.replaceDirectory);
            RainJava.LOGGER.info("========================================");
            return;
        }
        RainJava.LOGGER.info("Found {} replacement file(s)", (Object)javaFiles.size());
        for (Path file : javaFiles) {
            this.compileReplacement(file);
        }
        if (!this.compiledReplacements.isEmpty()) {
            this.applyReplacements();
        }
        RainJava.LOGGER.info("========================================");
        RainJava.LOGGER.info("Class Replacement Complete: {} classes compiled", (Object)this.compiledReplacements.size());
        RainJava.LOGGER.info("========================================");
    }

    private List<Path> scanJavaFiles() {
        ArrayList<Path> javaFiles = new ArrayList<Path>();
        try (Stream<Path> paths = Files.walk(this.replaceDirectory, new FileVisitOption[0]);){
            paths.filter(path -> path.toString().endsWith(".java")).forEach(javaFiles::add);
        }
        catch (Exception e) {
            RainJava.LOGGER.error("Failed to scan replace directory: {}", (Object)this.replaceDirectory, (Object)e);
        }
        return javaFiles;
    }

    private void compileReplacement(Path file) {
        try {
            RainJava.LOGGER.info("Compiling replacement: {}", (Object)file.getFileName());
            long startTime = System.currentTimeMillis();
            Path realFile = PathUtils.removeRainJavaPrefix(file);
            CompiledClass compiled = this.compiler.compile(realFile);
            long compileTime = System.currentTimeMillis() - startTime;
            String internalClassName = compiled.className.replace('.', '/');
            this.compiledReplacements.put(internalClassName, compiled.bytecode);
            RainJava.LOGGER.info("\u2713 Compiled: {} -> {} ({} bytes, {}ms)", (Object)file.getFileName(), (Object)internalClassName, (Object)compiled.bytecode.length, (Object)compileTime);
        }
        catch (Exception e) {
            RainJava.LOGGER.error("\u2717 Failed to compile: {}", (Object)file, (Object)e);
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
                RainJava.LOGGER.info("\u2713 Wrote replacement: {}", (Object)classFilePath);
                ++successCount;
            }
            RainJava.LOGGER.info("Successfully wrote {} replacement(s) to transformation directory", (Object)successCount);
            RainJava.LOGGER.info("Replacements will be applied on next game start");
        }
        catch (Exception e) {
            RainJava.LOGGER.error("Failed to write replacements to transformation directory", (Throwable)e);
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

