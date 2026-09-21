/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.gson.Gson
 *  com.google.gson.JsonObject
 *  org.apache.logging.log4j.Logger
 */
package com.rosetta.remotedebugbridge.mixin;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.apache.logging.log4j.Logger;

public class MixinJarBuilder {
    public static void buildMixinJar(Path classesDir, Path configFile, Path outputJar, Logger logger) throws IOException {
        logger.info("========================================");
        logger.info("Building Mixin JAR: {}", (Object)outputJar.getFileName());
        logger.info("========================================");
        logger.info("Source directories:");
        logger.info("  Classes: {}", (Object)classesDir);
        logger.info("  Config: {}", (Object)configFile);
        logger.info("  Output: {}", (Object)outputJar);
        if (!Files.exists(classesDir, new LinkOption[0])) {
            throw new IOException("Classes directory does not exist: " + String.valueOf(classesDir));
        }
        if (!Files.exists(configFile, new LinkOption[0])) {
            throw new IOException("Config file does not exist: " + String.valueOf(configFile));
        }
        String configPackage = MixinJarBuilder.extractPackageFromConfig(configFile, logger);
        Files.createDirectories(outputJar.getParent(), new FileAttribute[0]);
        if (Files.exists(outputJar, new LinkOption[0])) {
            Files.delete(outputJar);
            logger.info("Deleted old JAR file");
        }
        int classCount = 0;
        int totalSize = 0;
        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(outputJar, new OpenOption[0]), MixinJarBuilder.createManifest());){
            logger.info("Creating JAR with manifest...");
            String configName = configFile.getFileName().toString();
            logger.info("Adding config file: {}", (Object)configName);
            JarEntry configEntry = new JarEntry(configName);
            configEntry.setTime(System.currentTimeMillis());
            jos.putNextEntry(configEntry);
            byte[] configBytes = Files.readAllBytes(configFile);
            jos.write(configBytes);
            jos.closeEntry();
            logger.info("  \u2713 Added config: {} ({} bytes)", (Object)configName, (Object)configBytes.length);
            logger.info("Scanning for class files...");
            ClassFileResult result = MixinJarBuilder.addClassFiles(jos, classesDir, classesDir, configPackage, logger);
            classCount = result.count;
            totalSize = result.totalSize;
            if (classCount == 0) {
                throw new IOException("No class files were added to JAR! Check class file paths.");
            }
            jos.finish();
        }
        long jarSize = Files.size(outputJar);
        logger.info("========================================");
        logger.info("\u2713 Mixin JAR created successfully");
        logger.info("  File: {}", (Object)outputJar.getFileName());
        logger.info("  Size: {} bytes", (Object)jarSize);
        logger.info("  Classes: {}", (Object)classCount);
        logger.info("  Total class bytes: {}", (Object)totalSize);
        logger.info("========================================");
        MixinJarBuilder.verifyJarContents(outputJar, configPackage, logger);
    }

    private static String extractPackageFromConfig(Path configFile, Logger logger) {
        try {
            String content = Files.readString(configFile);
            JsonObject json = (JsonObject)new Gson().fromJson(content, JsonObject.class);
            if (json.has("package")) {
                String pkg = json.get("package").getAsString();
                logger.info("Config package: {}", (Object)(pkg.isEmpty() ? "(root)" : pkg));
                return pkg;
            }
        }
        catch (Exception e) {
            logger.warn("Could not extract package from config", (Throwable)e);
        }
        return "";
    }

    private static Manifest createManifest() {
        Manifest manifest = new Manifest();
        Attributes mainAttrs = manifest.getMainAttributes();
        mainAttrs.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        mainAttrs.putValue("MixinConfigs", "rosetta.mixins.json");
        mainAttrs.putValue("Created-By", "RosettaRemoteDebugBridge Mixin System");
        mainAttrs.putValue("Built-Date", String.valueOf(System.currentTimeMillis()));
        return manifest;
    }

    private static ClassFileResult addClassFiles(JarOutputStream jos, Path rootDir, Path currentDir, String expectedPackage, Logger logger) throws IOException {
        int count = 0;
        int totalSize = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(currentDir);){
            for (Path entry : stream) {
                if (Files.isDirectory(entry, new LinkOption[0])) {
                    ClassFileResult subResult = MixinJarBuilder.addClassFiles(jos, rootDir, entry, expectedPackage, logger);
                    count += subResult.count;
                    totalSize += subResult.totalSize;
                    continue;
                }
                if (!entry.toString().endsWith(".class")) continue;
                Path relativePath = rootDir.relativize(entry);
                String entryName = relativePath.toString().replace('\\', '/');
                String className = entryName.replace(".class", "").replace('/', '.');
                if (!expectedPackage.isEmpty() && !className.startsWith(expectedPackage)) {
                    logger.warn("  \u26a0 Class package mismatch: {} (expected to start with {})", (Object)className, (Object)expectedPackage);
                    logger.warn("    This may cause Mixin to fail loading the class!");
                }
                byte[] classBytes = Files.readAllBytes(entry);
                JarEntry jarEntry = new JarEntry(entryName);
                jarEntry.setTime(Files.getLastModifiedTime(entry, new LinkOption[0]).toMillis());
                jos.putNextEntry(jarEntry);
                jos.write(classBytes);
                jos.closeEntry();
                logger.info("  \u2713 Added: {} ({} bytes)", (Object)entryName, (Object)classBytes.length);
                ++count;
                totalSize += classBytes.length;
            }
        }
        return new ClassFileResult(count, totalSize);
    }

    private static void verifyJarContents(Path jarPath, String expectedPackage, Logger logger) {
        logger.info("Verifying JAR contents...");
        try (JarFile jarFile = new JarFile(jarPath.toFile());){
            Manifest manifest = jarFile.getManifest();
            if (manifest != null) {
                String mixinConfigs = manifest.getMainAttributes().getValue("MixinConfigs");
                logger.info("  MANIFEST.MF:");
                logger.info("    Manifest-Version: {}", (Object)manifest.getMainAttributes().getValue("Manifest-Version"));
                logger.info("    MixinConfigs: {}", (Object)mixinConfigs);
                if (mixinConfigs == null || !mixinConfigs.equals("rosetta.mixins.json")) {
                    logger.error("  \u2717 CRITICAL: MixinConfigs not set correctly!");
                }
            } else {
                logger.warn("  \u26a0 No MANIFEST.MF found in JAR");
            }
            logger.info("  JAR entries:");
            boolean hasConfig = false;
            int classCount = 0;
            ArrayList<String> mixinClasses = new ArrayList<String>();
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                long size = entry.getSize();
                if (name.equals("rosetta.mixins.json")) {
                    hasConfig = true;
                    logger.info("    \u2713 {} ({} bytes) - CONFIG FILE", (Object)name, (Object)size);
                    InputStream is = jarFile.getInputStream(entry);
                    try {
                        byte[] content = is.readAllBytes();
                        String configStr = new String(content);
                        JsonObject config = (JsonObject)new Gson().fromJson(configStr, JsonObject.class);
                        logger.info("      Config package: {}", (Object)(config.has("package") ? config.get("package").getAsString() : "(none)"));
                        if (config.has("mixins")) {
                            config.getAsJsonArray("mixins").forEach(e -> mixinClasses.add(e.getAsString()));
                        }
                        if (config.has("client")) {
                            config.getAsJsonArray("client").forEach(e -> mixinClasses.add(e.getAsString()));
                        }
                        if (config.has("server")) {
                            config.getAsJsonArray("server").forEach(e -> mixinClasses.add(e.getAsString()));
                        }
                        logger.info("      Declared mixins: {}", mixinClasses);
                        continue;
                    }
                    finally {
                        if (is != null) {
                            is.close();
                        }
                        continue;
                    }
                }
                if (!name.endsWith(".class")) continue;
                ++classCount;
                logger.info("    \u2713 {} ({} bytes)", (Object)name, (Object)size);
            }
            logger.info("  Summary:");
            logger.info("    Config file present: {}", (Object)hasConfig);
            logger.info("    Class files: {}", (Object)classCount);
            boolean hasErrors = false;
            if (!hasConfig) {
                logger.error("  \u2717 CRITICAL: Config file 'rosetta.mixins.json' not found in JAR!");
                hasErrors = true;
            }
            if (classCount == 0) {
                logger.error("  \u2717 CRITICAL: No class files found in JAR!");
                hasErrors = true;
            }
            if (hasConfig && !mixinClasses.isEmpty()) {
                logger.info("  Verifying mixin classes...");
                for (String mixinClass : mixinClasses) {
                    Object fullClassName = expectedPackage.isEmpty() ? mixinClass : expectedPackage + "." + mixinClass;
                    String classPath = ((String)fullClassName).replace('.', '/') + ".class";
                    JarEntry classEntry = jarFile.getJarEntry(classPath);
                    if (classEntry == null) {
                        logger.error("    \u2717 Mixin class NOT FOUND: {} (looked for: {})", fullClassName, (Object)classPath);
                        hasErrors = true;
                        continue;
                    }
                    logger.info("    \u2713 Mixin class found: {}", fullClassName);
                }
            }
            if (!hasErrors) {
                logger.info("  \u2713 JAR verification PASSED");
            } else {
                logger.error("  \u2717 JAR verification FAILED - Mixins will not load!");
            }
        }
        catch (IOException e2) {
            logger.error("Failed to verify JAR contents", (Throwable)e2);
        }
    }

    private static class ClassFileResult {
        final int count;
        final int totalSize;

        ClassFileResult(int count, int totalSize) {
            this.count = count;
            this.totalSize = totalSize;
        }
    }
}

