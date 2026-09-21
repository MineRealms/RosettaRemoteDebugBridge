/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.gson.Gson
 *  com.google.gson.JsonObject
 *  org.apache.logging.log4j.Logger
 */
package net.rain.rainjava.mixin;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.apache.logging.log4j.Logger;

public class MixinDebugHelper {
    public static boolean validateMixinSetup(Path jarFile, Path configFile, Logger logger) {
        logger.info("========================================");
        logger.info("MIXIN SETUP VALIDATION");
        logger.info("========================================");
        boolean hasErrors = false;
        try {
            String configContent = Files.readString(configFile);
            JsonObject config = (JsonObject)new Gson().fromJson(configContent, JsonObject.class);
            String packageName = config.has("package") ? config.get("package").getAsString() : "";
            logger.info("1. Config package: {}", (Object)(packageName.isEmpty() ? "(root)" : packageName));
            ArrayList<String> declaredMixins = new ArrayList<String>();
            if (config.has("mixins")) {
                config.getAsJsonArray("mixins").forEach(e -> declaredMixins.add(e.getAsString()));
            }
            if (config.has("client")) {
                config.getAsJsonArray("client").forEach(e -> declaredMixins.add(e.getAsString()));
            }
            if (config.has("server")) {
                config.getAsJsonArray("server").forEach(e -> declaredMixins.add(e.getAsString()));
            }
            logger.info("2. Declared mixin classes: {}", (Object)declaredMixins.size());
            declaredMixins.forEach(m -> logger.info("   - {}", m));
            if (!Files.exists(jarFile, new LinkOption[0])) {
                logger.error("\u2717 JAR file does not exist: {}", (Object)jarFile);
                return false;
            }
            logger.info("3. JAR file exists: {} ({} bytes)", (Object)jarFile, (Object)Files.size(jarFile));
            try (JarFile jar = new JarFile(jarFile.toFile());){
                JarEntry configEntry = jar.getJarEntry("rainjava.mixins.json");
                if (configEntry == null) {
                    logger.error("\u2717 CRITICAL: rainjava.mixins.json NOT in JAR root!");
                    hasErrors = true;
                } else {
                    logger.info("4. \u2713 Config file in JAR root");
                }
                logger.info("5. Verifying each mixin class...");
                for (String mixinClass : declaredMixins) {
                    Object fullClassName = packageName.isEmpty() ? mixinClass : packageName + "." + mixinClass;
                    String jarPath = ((String)fullClassName).replace('.', '/') + ".class";
                    JarEntry classEntry = jar.getJarEntry(jarPath);
                    if (classEntry == null) {
                        logger.error("   \u2717 NOT FOUND: {} (package: {}, looking for: {})", (Object)mixinClass, (Object)packageName, (Object)jarPath);
                        hasErrors = true;
                        logger.error("     Searching for this class in JAR...");
                        boolean found = false;
                        Enumeration<JarEntry> entries = jar.entries();
                        while (entries.hasMoreElements()) {
                            JarEntry entry = entries.nextElement();
                            String name = entry.getName();
                            if (!name.endsWith(mixinClass + ".class")) continue;
                            logger.error("     Found at: {} (WRONG LOCATION!)", (Object)name);
                            logger.error("     Expected: {}", (Object)jarPath);
                            found = true;
                        }
                        if (found) continue;
                        logger.error("     Class file not in JAR at all!");
                        continue;
                    }
                    logger.info("   \u2713 FOUND: {} -> {}", (Object)mixinClass, (Object)jarPath);
                }
                logger.info("6. All class files in JAR:");
                Enumeration<JarEntry> entries = jar.entries();
                int classCount = 0;
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    if (!entry.getName().endsWith(".class")) continue;
                    logger.info("   - {}", (Object)entry.getName());
                    ++classCount;
                }
                if (classCount == 0) {
                    logger.error("\u2717 CRITICAL: No class files in JAR!");
                    hasErrors = true;
                }
            }
        }
        catch (Exception e2) {
            logger.error("Validation failed with exception", (Throwable)e2);
            hasErrors = true;
        }
        logger.info("========================================");
        if (hasErrors) {
            logger.error("\u2717 VALIDATION FAILED - Mixins will NOT work!");
            logger.error("========================================");
            return false;
        }
        logger.info("\u2713 VALIDATION PASSED - Setup looks correct");
        logger.info("========================================");
        return true;
    }

    public static void showFixSuggestions(Path classesDir, Path configFile, Logger logger) {
        logger.info("========================================");
        logger.info("FIX SUGGESTIONS");
        logger.info("========================================");
        try {
            String configContent = Files.readString(configFile);
            JsonObject config = (JsonObject)new Gson().fromJson(configContent, JsonObject.class);
            String packageName = config.has("package") ? config.get("package").getAsString() : "";
            logger.info("Config declares package: {}", (Object)(packageName.isEmpty() ? "(root)" : packageName));
            logger.info("Scanning actual class files in: {}", (Object)classesDir);
            if (!Files.exists(classesDir, new LinkOption[0])) {
                logger.error("Classes directory does not exist!");
                return;
            }
            Files.walk(classesDir, new FileVisitOption[0]).filter(p -> p.toString().endsWith(".class")).forEach(classFile -> {
                Path relative = classesDir.relativize((Path)classFile);
                String actualPath = relative.toString().replace('\\', '/');
                String actualPackage = actualPath.replace(".class", "").replace('/', '.');
                logger.info("  Found class: {}", (Object)actualPath);
                logger.info("    Full name: {}", (Object)actualPackage);
                if (!packageName.isEmpty() && !actualPackage.startsWith(packageName)) {
                    logger.warn("    \u26a0 MISMATCH! This doesn't start with '{}'", (Object)packageName);
                    logger.warn("    The class should be at: {}", (Object)(packageName.replace('.', '/') + "/" + String.valueOf(classFile.getFileName())));
                }
            });
            logger.info("========================================");
            logger.info("RECOMMENDATIONS:");
            logger.info("1. Make sure your .java source files have correct 'package' declarations");
            logger.info("2. The package in source must match the directory structure");
            logger.info("3. Example: if package is 'rainjava.startup.mixins', file should be at:");
            logger.info("   RainJava/startup/mixins/ItemStackMixin.java");
            logger.info("4. After fixing, delete .mixin folder and restart");
            logger.info("========================================");
        }
        catch (Exception e) {
            logger.error("Failed to show suggestions", (Throwable)e);
        }
    }
}

