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
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import org.apache.logging.log4j.Logger;

public class MixinDiagnosticTool {
    public static void diagnose(Path gameDir, Logger logger) {
        logger.info("========================================");
        logger.info("MIXIN DIAGNOSTIC TOOL");
        logger.info("========================================");
        Path mixinDir = gameDir.resolve(".mixin");
        Path configDir = mixinDir.resolve("mixins_config");
        Path classesDir = mixinDir.resolve("mixins").resolve("classes");
        Path configFile = configDir.resolve("rosetta.mixins.json");
        Path jarFile = configDir.resolve("rosetta.mixins.jar");
        logger.info("1. Checking directory structure...");
        MixinDiagnosticTool.checkPath(mixinDir, "Mixin base directory", logger);
        MixinDiagnosticTool.checkPath(configDir, "Config directory", logger);
        MixinDiagnosticTool.checkPath(classesDir, "Classes directory", logger);
        MixinDiagnosticTool.checkPath(configFile, "Config file", logger);
        MixinDiagnosticTool.checkPath(jarFile, "JAR file", logger);
        if (Files.exists(configFile, new LinkOption[0])) {
            logger.info("\n2. Analyzing config file...");
            MixinDiagnosticTool.analyzeConfigFile(configFile, logger);
        }
        if (Files.exists(classesDir, new LinkOption[0])) {
            logger.info("\n3. Checking compiled classes...");
            MixinDiagnosticTool.checkClassFiles(classesDir, logger);
        }
        if (Files.exists(jarFile, new LinkOption[0])) {
            logger.info("\n4. Analyzing JAR file...");
            MixinDiagnosticTool.analyzeJarFile(jarFile, logger);
        }
        logger.info("\n5. Checking classpath...");
        MixinDiagnosticTool.checkClasspath(logger);
        logger.info("\n6. Testing config accessibility...");
        MixinDiagnosticTool.testConfigAccess(logger);
        logger.info("\n7. Recommendations:");
        MixinDiagnosticTool.giveRecommendations(gameDir, logger);
        logger.info("========================================");
        logger.info("DIAGNOSTIC COMPLETE");
        logger.info("========================================");
    }

    private static void checkPath(Path path, String description, Logger logger) {
        block5: {
            String type;
            boolean exists = Files.exists(path, new LinkOption[0]);
            String string = type = Files.isDirectory(path, new LinkOption[0]) ? "Directory" : "File";
            if (exists) {
                try {
                    if (Files.isRegularFile(path, new LinkOption[0])) {
                        long size = Files.size(path);
                        logger.info("  \u2713 {} exists: {} ({} bytes)", (Object)description, (Object)path, (Object)size);
                        break block5;
                    }
                    logger.info("  \u2713 {} exists: {}", (Object)description, (Object)path);
                }
                catch (IOException e) {
                    logger.warn("  \u26a0 {} exists but cannot read: {}", (Object)description, (Object)path);
                }
            } else {
                logger.warn("  \u2717 {} does NOT exist: {}", (Object)description, (Object)path);
            }
        }
    }

    private static void analyzeConfigFile(Path configFile, Logger logger) {
        try {
            String content = Files.readString(configFile);
            logger.info("  Config file size: {} bytes", (Object)content.length());
            logger.info("  Config content:");
            JsonObject json = (JsonObject)new Gson().fromJson(content, JsonObject.class);
            logger.info("    package: {}", (Object)(json.has("package") ? json.get("package").getAsString() : "(none)"));
            logger.info("    required: {}", (Object)(json.has("required") ? json.get("required").getAsBoolean() : false));
            logger.info("    minVersion: {}", (Object)(json.has("minVersion") ? json.get("minVersion").getAsString() : "(none)"));
            logger.info("    compatibilityLevel: {}", (Object)(json.has("compatibilityLevel") ? json.get("compatibilityLevel").getAsString() : "(none)"));
            if (json.has("mixins")) {
                logger.info("    mixins (common): {}", (Object)json.getAsJsonArray("mixins"));
            }
            if (json.has("client")) {
                logger.info("    client: {}", (Object)json.getAsJsonArray("client"));
            }
            if (json.has("server")) {
                logger.info("    server: {}", (Object)json.getAsJsonArray("server"));
            }
        }
        catch (Exception e) {
            logger.error("  \u2717 Failed to analyze config file", (Throwable)e);
        }
    }

    private static void checkClassFiles(Path classesDir, Logger logger) {
        try {
            ArrayList<Path> classFiles = new ArrayList<Path>();
            Files.walk(classesDir, new FileVisitOption[0]).filter(p -> p.toString().endsWith(".class")).forEach(classFiles::add);
            logger.info("  Found {} class file(s):", (Object)classFiles.size());
            for (Path classFile : classFiles) {
                Path relative = classesDir.relativize(classFile);
                String className = relative.toString().replace(File.separatorChar, '/').replace(".class", "");
                long size = Files.size(classFile);
                logger.info("    \u2713 {} ({} bytes)", (Object)className, (Object)size);
            }
            if (classFiles.isEmpty()) {
                logger.warn("  \u2717 No class files found! Mixins need to be compiled first.");
            }
        }
        catch (IOException e) {
            logger.error("  \u2717 Failed to check class files", (Throwable)e);
        }
    }

    private static void analyzeJarFile(Path jarFile, Logger logger) {
        try {
            long jarSize = Files.size(jarFile);
            logger.info("  JAR file size: {} bytes", (Object)jarSize);
            if (jarSize == 0L) {
                logger.error("  \u2717 JAR file is EMPTY!");
                return;
            }
            try (JarFile jar = new JarFile(jarFile.toFile());){
                Manifest manifest = jar.getManifest();
                if (manifest != null) {
                    logger.info("  \u2713 MANIFEST.MF found:");
                    manifest.getMainAttributes().forEach((key, value) -> logger.info("      {}: {}", key, value));
                } else {
                    logger.warn("  \u26a0 No MANIFEST.MF in JAR");
                }
                boolean hasConfig = false;
                int classCount = 0;
                ArrayList<String> entries = new ArrayList<String>();
                Enumeration<JarEntry> jarEntries = jar.entries();
                while (jarEntries.hasMoreElements()) {
                    JarEntry entry = jarEntries.nextElement();
                    String name = entry.getName();
                    entries.add(name);
                    if (name.equals("rosetta.mixins.json")) {
                        hasConfig = true;
                        InputStream is = jar.getInputStream(entry);
                        try {
                            String configContent = new String(is.readAllBytes());
                            logger.info("  \u2713 Config file found in JAR:");
                            logger.info("      Size: {} bytes", (Object)configContent.length());
                            logger.info("      Preview: {}", (Object)(configContent.substring(0, Math.min(100, configContent.length())) + "..."));
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
                }
                logger.info("  JAR summary:");
                logger.info("    Total entries: {}", (Object)entries.size());
                logger.info("    Config present: {}", (Object)hasConfig);
                logger.info("    Class files: {}", (Object)classCount);
                if (!hasConfig) {
                    logger.error("  \u2717 CRITICAL: rosetta.mixins.json NOT in JAR!");
                    logger.info("  Available entries:");
                    entries.forEach(e -> logger.info("      {}", e));
                }
                if (classCount == 0) {
                    logger.error("  \u2717 CRITICAL: No .class files in JAR!");
                }
            }
        }
        catch (IOException e2) {
            logger.error("  \u2717 Failed to analyze JAR file", (Throwable)e2);
        }
    }

    private static void checkClasspath(Logger logger) {
        String classpath = System.getProperty("java.class.path");
        logger.info("  System classpath entries:");
        if (classpath != null) {
            String[] entries;
            for (String entry : entries = classpath.split(File.pathSeparator)) {
                if (!entry.contains("rosetta_remote_debug_bridge") && !entry.contains("mixin")) continue;
                logger.info("    \u2713 {}", (Object)entry);
            }
        }
        ClassLoader[] loaders = new ClassLoader[]{Thread.currentThread().getContextClassLoader(), ClassLoader.getSystemClassLoader(), MixinDiagnosticTool.class.getClassLoader()};
        logger.info("  Available ClassLoaders:");
        for (int i = 0; i < loaders.length; ++i) {
            if (loaders[i] == null) continue;
            logger.info("    {}: {}", (Object)i, (Object)loaders[i].getClass().getName());
        }
    }

    private static void testConfigAccess(Logger logger) {
        String resourceName = "rosetta.mixins.json";
        ClassLoader[] loaders = new ClassLoader[]{Thread.currentThread().getContextClassLoader(), ClassLoader.getSystemClassLoader(), MixinDiagnosticTool.class.getClassLoader()};
        boolean found = false;
        for (int i = 0; i < loaders.length; ++i) {
            if (loaders[i] == null) continue;
            URL url = loaders[i].getResource(resourceName);
            if (url != null) {
                logger.info("  \u2713 Found via ClassLoader #{}: {}", (Object)i, (Object)url);
                try (InputStream is = url.openStream();){
                    byte[] content = is.readAllBytes();
                    logger.info("    Content accessible: {} bytes", (Object)content.length);
                    found = true;
                }
                catch (IOException e) {
                    logger.warn("    \u26a0 Cannot read content: {}", (Object)e.getMessage());
                }
                continue;
            }
            logger.info("  \u2717 Not found via ClassLoader #{}", (Object)i);
        }
        if (!found) {
            logger.error("  \u2717 CRITICAL: Config file NOT accessible from any ClassLoader!");
            logger.error("  This means the JAR is not properly loaded into classpath.");
        }
    }

    private static void giveRecommendations(Path gameDir, Logger logger) {
        ClassLoader cl;
        URL configUrl;
        Path jarFile = gameDir.resolve(".mixin/mixins_config/rosetta.mixins.jar");
        Path configFile = gameDir.resolve(".mixin/mixins_config/rosetta.mixins.json");
        Path classesDir = gameDir.resolve(".mixin/mixins/classes");
        ArrayList<String> issues = new ArrayList<String>();
        ArrayList<String> recommendations = new ArrayList<String>();
        if (!Files.exists(jarFile, new LinkOption[0])) {
            issues.add("JAR file does not exist");
            recommendations.add("Run the game once to generate Mixin JAR from source files");
        } else {
            try {
                if (Files.size(jarFile) == 0L) {
                    issues.add("JAR file is empty");
                    recommendations.add("Delete .mixin folder and regenerate");
                }
            }
            catch (IOException iOException) {
                // empty catch block
            }
        }
        if (!Files.exists(configFile, new LinkOption[0])) {
            issues.add("Config file does not exist");
            recommendations.add("Ensure MixinManager.generateMixinConfig() is called during startup");
        }
        if (!Files.exists(classesDir, new LinkOption[0])) {
            issues.add("Classes directory does not exist");
            recommendations.add("Ensure MixinManager.compileMixins() is called before generating config");
        }
        if ((configUrl = (cl = Thread.currentThread().getContextClassLoader()).getResource("rosetta.mixins.json")) == null) {
            issues.add("Config not in classpath");
            recommendations.add("JAR needs to be added to classpath BEFORE Mixin initialization");
            recommendations.add("Current workaround: Restart game after first generation");
        }
        if (issues.isEmpty()) {
            logger.info("  \u2713 No obvious issues detected!");
            logger.info("  If Mixins still don't work, check:");
            logger.info("    - Mixin class has @Mixin annotation");
            logger.info("    - Target class name is correct");
            logger.info("    - Injection points are valid");
        } else {
            logger.warn("  Issues detected: {}", (Object)issues.size());
            issues.forEach(issue -> logger.warn("    \u2022 {}", issue));
            logger.info("  Recommendations:");
            recommendations.forEach(rec -> logger.info("    \u2192 {}", rec));
        }
        logger.info("\n  Quick fix steps:");
        logger.info("    1. Stop the game");
        logger.info("    2. Delete: {}", (Object)gameDir.resolve(".mixin"));
        logger.info("    3. Start the game (generates JAR)");
        logger.info("    4. Restart the game (loads Mixins)");
    }
}

