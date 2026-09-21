/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraftforge.fml.util.ObfuscationReflectionHelper
 *  org.apache.logging.log4j.LogManager
 *  org.apache.logging.log4j.Logger
 */
package com.rosetta.remotedebugbridge.script.utils;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MinecraftHelper {
    public static final Logger LOGGER = LogManager.getLogger();
    public static final Map<String, Field> fieldCache = new ConcurrentHashMap<String, Field>();
    public static final Map<String, Method> methodCache = new ConcurrentHashMap<String, Method>();
    public static final Map<String, String> fieldMappings = new ConcurrentHashMap<String, String>();
    public static final Map<String, String> methodMappings = new ConcurrentHashMap<String, String>();
    public static final Map<String, String> classNameMappings = new ConcurrentHashMap<String, String>();
    public static final Map<String, String> methodReturnTypes = new ConcurrentHashMap<String, String>();
    public static final Map<String, String> methodMappingsByDescriptor = new ConcurrentHashMap<String, String>();
    public static final Map<String, String> methodReturnTypesByDescriptor = new ConcurrentHashMap<String, String>();
    public static final Map<String, String> superClassCache = new ConcurrentHashMap<String, String>();
    public static final String MAPPING_RESOURCE_PATH = "/assets/mappings/map/mappings.tsrg";
    public static boolean mappingLoaded = false;
    public static Path mappingFilePath = null;
    private static volatile Boolean srgRuntime;

    public static boolean isSrgRuntime() {
        Boolean cached = srgRuntime;
        if (cached != null) {
            return cached;
        }
        boolean result = false;
        try {
            Class<?> itemStack = Class.forName("net.minecraft.world.item.ItemStack");
            try {
                itemStack.getDeclaredField("EMPTY");
                result = false;
            }
            catch (NoSuchFieldException officialMissing) {
                try {
                    itemStack.getDeclaredField("f_41583_");
                    result = true;
                }
                catch (NoSuchFieldException srgMissing) {
                    result = false;
                }
            }
        }
        catch (Throwable ignored) {
            result = false;
        }
        srgRuntime = result;
        LOGGER.info("[MinecraftHelper] Runtime naming detected: {}", (Object)(result ? "SRG (production)" : "official (development)"));
        return result;
    }

    public static String normalizeClassName(String className) {
        if (className == null) {
            return null;
        }
        return className.replace('$', '.').replace('/', '.');
    }

    public static void loadMappingsFromResource() {
        String[] possiblePaths;
        if (mappingLoaded) {
            return;
        }
        mappingLoaded = true;
        for (String path : possiblePaths = new String[]{"/assets/mappings/map/mappings.tsrg", "assets/mappings/map/mappings.tsrg", "/mappings.tsrg", "mappings.tsrg"}) {
            String stripped;
            block42: {
                block41: {
                    try (InputStream is = MinecraftHelper.class.getResourceAsStream(path);){
                        if (is == null) break block41;
                        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is));){
                            MinecraftHelper.parseMappingFile(reader);
                            LOGGER.info("Loaded: {} classes, {} fields, {} methods from {}", (Object)(classNameMappings.size() / 2), (Object)fieldMappings.size(), (Object)methodMappings.size(), (Object)path);
                        }
                        return;
                    }
                    catch (Exception e) {
                        LOGGER.debug("Class.getResourceAsStream failed ({}): {}", (Object)path, (Object)e.getMessage());
                    }
                }
                stripped = path.startsWith("/") ? path.substring(1) : path;
                try (InputStream is = MinecraftHelper.class.getClassLoader().getResourceAsStream(stripped);){
                    if (is == null) break block42;
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(is));){
                        MinecraftHelper.parseMappingFile(reader);
                        LOGGER.info("Loaded via ClassLoader from {}", (Object)path);
                    }
                    return;
                }
                catch (Exception e) {
                    LOGGER.debug("ClassLoader failed ({}): {}", (Object)path, (Object)e.getMessage());
                }
            }
            try {
                ClassLoader ctx = Thread.currentThread().getContextClassLoader();
                if (ctx == null) continue;
                try (InputStream is = ctx.getResourceAsStream(stripped);){
                    if (is == null) continue;
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(is));){
                        MinecraftHelper.parseMappingFile(reader);
                        LOGGER.info("Loaded via ContextClassLoader from {}", (Object)path);
                    }
                    return;
                }
            }
            catch (Exception e) {
                LOGGER.debug("ContextClassLoader failed ({}): {}", (Object)path, (Object)e.getMessage());
            }
        }
        LOGGER.warn("Mapping file not found in resources, will rely on ObfuscationReflectionHelper");
    }

    public static InputStream getResourceAsStream(String resourcePath) {
        InputStream is = MinecraftHelper.class.getResourceAsStream(resourcePath);
        if (is != null) {
            return is;
        }
        String stripped = resourcePath.startsWith("/") ? resourcePath.substring(1) : resourcePath;
        is = MinecraftHelper.class.getClassLoader().getResourceAsStream(stripped);
        if (is != null) {
            return is;
        }
        ClassLoader ctx = Thread.currentThread().getContextClassLoader();
        if (ctx != null && (is = ctx.getResourceAsStream(stripped)) != null) {
            return is;
        }
        LOGGER.warn("Resource not found: {}", (Object)resourcePath);
        return null;
    }

    public static boolean resourceExists(String resourcePath) {
        try (InputStream is = MinecraftHelper.getResourceAsStream(resourcePath)) {
            return is != null;
        }
        catch (Exception e) {
            return false;
        }
    }

    public static void findMappingFile() {
        String[] possiblePaths;
        if (mappingFilePath != null) {
            return;
        }
        for (String pathStr : possiblePaths = new String[]{"config/forge/mappings.tsrg", "mappings/mappings.tsrg", ".gradle/caches/forge_gradle/mcp_mappings/mappings.tsrg"}) {
            Path path = Paths.get(pathStr, new String[0]);
            if (!Files.exists(path, new LinkOption[0])) continue;
            mappingFilePath = path;
            LOGGER.info("Found mapping file: {}", (Object)path);
            return;
        }
        LOGGER.debug("Mapping file not found in file system");
    }

    public static void parseMappingFile(BufferedReader reader) throws Exception {
        String line;
        String currentClass = null;
        boolean isFirstLine = true;
        int lineNumber = 0;
        while ((line = reader.readLine()) != null) {
            String[] parts;
            ++lineNumber;
            String originalLine = line;
            if ((line = line.trim()).isEmpty() || line.startsWith("#")) continue;
            if (isFirstLine && line.startsWith("tsrg2")) {
                isFirstLine = false;
                continue;
            }
            isFirstLine = false;
            int tabCount = 0;
            for (char c : originalLine.toCharArray()) {
                if (c != '\t') break;
                ++tabCount;
            }
            if (tabCount == 0) {
                parts = line.split("\\s+");
                if (parts.length < 2) continue;
                currentClass = MinecraftHelper.normalizeClassName(parts[1]);
                classNameMappings.put(currentClass, currentClass);
                String simpleName = currentClass.contains(".") ? currentClass.substring(currentClass.lastIndexOf(46) + 1) : currentClass;
                classNameMappings.putIfAbsent(simpleName, currentClass);
                continue;
            }
            if (currentClass == null || tabCount != 1 || (parts = line.split("\\s+")).length < 2) continue;
            String mcpName = parts[0];
            if (parts[1].startsWith("(")) {
                String returnType;
                if (parts.length < 3) continue;
                String fullDescriptor = parts[1];
                String srgName = parts[2];
                if (!srgName.startsWith("m_") && !srgName.equals("<init>") && !srgName.equals("<clinit>")) continue;
                String keySimple = currentClass + "." + mcpName;
                methodMappings.put(keySimple, srgName);
                String returnDesc = MinecraftHelper.extractReturnTypeDescriptor(fullDescriptor);
                if (returnDesc == null || (returnType = MinecraftHelper.jvmDescriptorToClassName(returnDesc)) == null) continue;
                methodReturnTypes.put(keySimple, returnType);
                String paramDesc = MinecraftHelper.extractParamDescriptor(fullDescriptor);
                if (paramDesc == null) continue;
                String keyWithDesc = keySimple + paramDesc;
                methodMappingsByDescriptor.put(keyWithDesc, srgName);
                methodReturnTypesByDescriptor.put(keyWithDesc, returnType);
                continue;
            }
            String srgName = parts[1];
            if (!srgName.startsWith("f_")) continue;
            fieldMappings.put(currentClass + "." + mcpName, srgName);
        }
        LOGGER.info("Parsed: {} classes, {} fields, {} methods (simple), {} methods (desc)", (Object)(classNameMappings.size() / 2), (Object)fieldMappings.size(), (Object)methodMappings.size(), (Object)methodMappingsByDescriptor.size());
    }

    private static String extractParamDescriptor(String full) {
        int close = full.indexOf(41);
        return close < 0 ? null : full.substring(0, close + 1);
    }

    private static String extractReturnTypeDescriptor(String full) {
        int close = full.indexOf(41);
        if (close < 0 || close + 1 >= full.length()) {
            return null;
        }
        return full.substring(close + 1);
    }

    public static String jvmDescriptorToClassName(String desc) {
        if (desc == null || desc.isEmpty()) {
            return null;
        }
        if (desc.startsWith("[")) {
            String comp = MinecraftHelper.jvmDescriptorToClassName(desc.substring(1));
            return comp != null ? comp + "[]" : null;
        }
        switch (desc.charAt(0)) {
            case 'V': {
                return "void";
            }
            case 'I': {
                return "int";
            }
            case 'J': {
                return "long";
            }
            case 'F': {
                return "float";
            }
            case 'D': {
                return "double";
            }
            case 'Z': {
                return "boolean";
            }
            case 'C': {
                return "char";
            }
            case 'B': {
                return "byte";
            }
            case 'S': {
                return "short";
            }
            case 'L': {
                return desc.endsWith(";") ? MinecraftHelper.normalizeClassName(desc.substring(1, desc.length() - 1)) : null;
            }
        }
        return null;
    }

    public static String findFullClassName(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        if (!mappingLoaded) {
            MinecraftHelper.loadMappingsFromResource();
        }
        return classNameMappings.get(MinecraftHelper.normalizeClassName(name));
    }

    public static String findSuperClassName(String className) {
        if (className == null || className.isEmpty()) {
            return null;
        }
        String normalized = MinecraftHelper.normalizeClassName(className);
        String cached = superClassCache.get(normalized);
        if (cached != null) {
            return cached.isEmpty() ? null : cached;
        }
        String result = MinecraftHelper.reflectSuperClassName(normalized);
        superClassCache.put(normalized, result != null ? result : "");
        if (result != null) {
            LOGGER.debug("Resolved super class via reflection: {} -> {}", (Object)normalized, (Object)result);
        } else {
            LOGGER.debug("No meaningful super class found for: {}", (Object)normalized);
        }
        return result;
    }

    private static String reflectSuperClassName(String className) {
        try {
            Class<?> clazz = Class.forName(className);
            return MinecraftHelper.extractMeaningfulSuperName(clazz);
        }
        catch (ClassNotFoundException clazz) {
            try {
                ClassLoader mcl = Thread.currentThread().getContextClassLoader();
                if (mcl != null) {
                    Class<?> clazz2 = mcl.loadClass(className);
                    return MinecraftHelper.extractMeaningfulSuperName(clazz2);
                }
            }
            catch (ClassNotFoundException mcl) {
                // empty catch block
            }
            try {
                ClassLoader forgeLoader = MinecraftHelper.class.getClassLoader();
                if (forgeLoader != null) {
                    Class<?> clazz3 = forgeLoader.loadClass(className);
                    return MinecraftHelper.extractMeaningfulSuperName(clazz3);
                }
            }
            catch (ClassNotFoundException ignored) {
                LOGGER.debug("Cannot load class for super-class resolution: {}", (Object)className);
            }
            return null;
        }
    }

    private static String extractMeaningfulSuperName(Class<?> clazz) {
        if (clazz == null || clazz.isInterface() || clazz.isArray() || clazz.isPrimitive()) {
            return null;
        }
        Class<?> superClass = clazz.getSuperclass();
        if (superClass == null || superClass == Object.class) {
            return null;
        }
        return superClass.getName();
    }

    /*
     * Enabled aggressive block sorting
     * Enabled unnecessary exception pruning
     * Enabled aggressive exception aggregation
     */
    public static String findSrgFieldName(String className, String mcpName) {
        String key = (className = MinecraftHelper.normalizeClassName(className)) + "." + mcpName;
        if (fieldMappings.containsKey(key)) {
            return fieldMappings.get(key);
        }
        if (!mappingLoaded) {
            MinecraftHelper.loadMappingsFromResource();
            if (fieldMappings.containsKey(key)) {
                return fieldMappings.get(key);
            }
        }
        MinecraftHelper.findMappingFile();
        if (mappingFilePath == null) return null;
        if (!Files.exists(mappingFilePath, new LinkOption[0])) return null;
        try (BufferedReader reader = new BufferedReader(new FileReader(mappingFilePath.toFile()));){
            String[] parts;
            block14: {
                String line;
                boolean inTarget = false;
                while ((line = reader.readLine()) != null) {
                    if ((line = line.trim()).isEmpty() || line.startsWith("#")) continue;
                    if (!line.startsWith("\t") && !line.startsWith("    ")) {
                        parts = line.split("\\s+");
                        if (parts.length < 2) continue;
                        inTarget = MinecraftHelper.normalizeClassName(parts[1]).equals(className);
                        continue;
                    }
                    if (!(inTarget && (parts = line.trim().split("\\s+")).length >= 2 && parts[1].startsWith("f_") && parts[0].equals(mcpName))) {
                        continue;
                    }
                    break block14;
                }
                return null;
            }
            fieldMappings.put(key, parts[1]);
            String string = parts[1];
            return string;
        }
        catch (Exception e) {
            LOGGER.debug("Error mapping field {}.{}: {}", (Object)className, (Object)mcpName, (Object)e.getMessage());
        }
        return null;
    }

    /*
     * Enabled aggressive block sorting
     * Enabled unnecessary exception pruning
     * Enabled aggressive exception aggregation
     */
    public static String findSrgMethodName(String className, String mcpName) {
        String key = (className = MinecraftHelper.normalizeClassName(className)) + "." + mcpName;
        if (methodMappings.containsKey(key)) {
            return methodMappings.get(key);
        }
        if (!mappingLoaded) {
            MinecraftHelper.loadMappingsFromResource();
            if (methodMappings.containsKey(key)) {
                return methodMappings.get(key);
            }
        }
        MinecraftHelper.findMappingFile();
        if (mappingFilePath == null) return null;
        if (!Files.exists(mappingFilePath, new LinkOption[0])) return null;
        try (BufferedReader reader = new BufferedReader(new FileReader(mappingFilePath.toFile()));){
            String srg;
            block15: {
                String line;
                String curClass = null;
                while ((line = reader.readLine()) != null) {
                    String[] p;
                    char c;
                    String orig = line;
                    if ((line = line.trim()).isEmpty() || line.startsWith("#")) continue;
                    int tabs = 0;
                    char[] cArray = orig.toCharArray();
                    int n = cArray.length;
                    for (int i = 0; i < n && (c = cArray[i]) == '\t'; ++tabs, ++i) {
                    }
                    if (tabs == 0) {
                        p = line.split("\\s+");
                        if (p.length < 2) continue;
                        curClass = MinecraftHelper.normalizeClassName(p[1]);
                        continue;
                    }
                    if (!(curClass != null && curClass.equals(className) && tabs == 1 && (p = line.split("\\s+")).length >= 3 && p[1].startsWith("(") && p[0].equals(mcpName) && ((srg = p[2]).startsWith("m_") || srg.equals("<init>") || srg.equals("<clinit>")))) {
                        continue;
                    }
                    break block15;
                }
                return null;
            }
            methodMappings.put(key, srg);
            String string = srg;
            return string;
        }
        catch (Exception e) {
            LOGGER.debug("Error mapping method {}.{}: {}", (Object)className, (Object)mcpName, (Object)e.getMessage());
        }
        return null;
    }

    public static String findSrgMethodName(String className, String mcpName, String descriptor) {
        String result;
        if (descriptor == null) {
            return MinecraftHelper.findSrgMethodName(className, mcpName);
        }
        className = MinecraftHelper.normalizeClassName(className);
        if (!mappingLoaded) {
            MinecraftHelper.loadMappingsFromResource();
        }
        return (result = methodMappingsByDescriptor.get(className + "." + mcpName + descriptor)) != null ? result : MinecraftHelper.findSrgMethodName(className, mcpName);
    }

    public static String findMethodReturnType(String className, String mcpName) {
        className = MinecraftHelper.normalizeClassName(className);
        if (!mappingLoaded) {
            MinecraftHelper.loadMappingsFromResource();
        }
        return methodReturnTypes.get(className + "." + mcpName);
    }

    public static String findMethodReturnType(String className, String mcpName, String descriptor) {
        String result;
        if (descriptor == null) {
            return MinecraftHelper.findMethodReturnType(className, mcpName);
        }
        className = MinecraftHelper.normalizeClassName(className);
        if (!mappingLoaded) {
            MinecraftHelper.loadMappingsFromResource();
        }
        return (result = methodReturnTypesByDescriptor.get(className + "." + mcpName + descriptor)) != null ? result : MinecraftHelper.findMethodReturnType(className, mcpName);
    }

    public static String findFieldType(String className, String fieldName) {
        if (className == null || className.isEmpty() || fieldName == null || fieldName.isEmpty()) {
            return null;
        }
        String normalized = MinecraftHelper.normalizeClassName(className);
        try {
            Class<?> clazz = Class.forName(normalized);
            if (!mappingLoaded) {
                MinecraftHelper.loadMappingsFromResource();
            }
            String srgName = MinecraftHelper.findSrgFieldName(normalized, fieldName);
            for (Class<?> current = clazz; current != null && current != Object.class; current = current.getSuperclass()) {
                for (Field field : current.getDeclaredFields()) {
                    if (field.getName().equals(fieldName) || srgName != null && field.getName().equals(srgName)) {
                        return field.getType().getName();
                    }
                }
            }
        }
        catch (Throwable t) {
            LOGGER.debug("findFieldType failed for {}.{}: {}", (Object)className, (Object)fieldName, (Object)t.getMessage());
        }
        return null;
    }

    public static <T> T getStaticField(Class<?> clazz, String mcpName) {
        String cacheKey = clazz.getName() + "." + mcpName;
        Field field = fieldCache.computeIfAbsent(cacheKey, k -> {
            try {
                Field f = clazz.getDeclaredField(mcpName);
                f.setAccessible(true);
                return f;
            }
            catch (NoSuchFieldException f) {
                try {
                    Field f2 = ObfuscationReflectionHelper.findField((Class)clazz, (String)mcpName);
                    f2.setAccessible(true);
                    return f2;
                }
                catch (Exception e) {
                    LOGGER.debug("ObfuscationReflectionHelper failed for {}.{}: {}", (Object)clazz.getName(), (Object)mcpName, (Object)e.getMessage());
                    String srgName = MinecraftHelper.findSrgFieldName(clazz.getName(), mcpName);
                    if (srgName != null) {
                        try {
                            Field f3 = clazz.getDeclaredField(srgName);
                            f3.setAccessible(true);
                            return f3;
                        }
                        catch (NoSuchFieldException f3) {
                            // empty catch block
                        }
                    }
                    for (Class cur = clazz; cur != null && cur != Object.class; cur = cur.getSuperclass()) {
                        for (Field f4 : cur.getDeclaredFields()) {
                            if (!f4.getName().equals(mcpName) && (srgName == null || !f4.getName().equals(srgName))) continue;
                            f4.setAccessible(true);
                            return f4;
                        }
                    }
                    LOGGER.warn("Failed to find field {}. Available in {}:", (Object)mcpName, (Object)clazz.getName());
                    for (Field f4 : clazz.getDeclaredFields()) {
                        LOGGER.warn("  - {} ({})", (Object)f4.getName(), (Object)f4.getType().getSimpleName());
                    }
                    throw new RuntimeException("Field not found: " + mcpName + " in " + clazz.getName() + (String)(srgName != null ? " (tried SRG: " + srgName + ")" : ""));
                }
            }
        });
        try {
            return (T)field.get(null);
        }
        catch (Exception e) {
            throw new RuntimeException("Failed to get static field: " + mcpName, e);
        }
    }

    public static void setStaticField(Class<?> clazz, String mcpName, Object value) {
        String cacheKey = clazz.getName() + "." + mcpName;
        Field field = fieldCache.computeIfAbsent(cacheKey, k -> {
            try {
                Field f = clazz.getDeclaredField(mcpName);
                f.setAccessible(true);
                return f;
            }
            catch (NoSuchFieldException f) {
                try {
                    Field f2 = ObfuscationReflectionHelper.findField((Class)clazz, (String)mcpName);
                    f2.setAccessible(true);
                    return f2;
                }
                catch (Exception f2) {
                    String srg = MinecraftHelper.findSrgFieldName(clazz.getName(), mcpName);
                    if (srg != null) {
                        try {
                            Field f3 = clazz.getDeclaredField(srg);
                            f3.setAccessible(true);
                            return f3;
                        }
                        catch (NoSuchFieldException noSuchFieldException) {
                            // empty catch block
                        }
                    }
                    throw new RuntimeException("Field not found: " + mcpName + " in " + clazz.getName());
                }
            }
        });
        try {
            field.set(null, value);
        }
        catch (Exception e) {
            throw new RuntimeException("Failed to set static field: " + mcpName, e);
        }
    }

    public static <T> T getField(Object obj, String mcpName) {
        if (obj == null) {
            throw new IllegalArgumentException("Object cannot be null");
        }
        Class<?> clazz = obj.getClass();
        String cacheKey = clazz.getName() + "." + mcpName;
        Field field = fieldCache.computeIfAbsent(cacheKey, k -> {
            try {
                Field f = clazz.getDeclaredField(mcpName);
                f.setAccessible(true);
                return f;
            }
            catch (NoSuchFieldException f) {
                try {
                    Field f2 = ObfuscationReflectionHelper.findField((Class)clazz, (String)mcpName);
                    f2.setAccessible(true);
                    return f2;
                }
                catch (Exception f2) {
                    String srg = MinecraftHelper.findSrgFieldName(clazz.getName(), mcpName);
                    if (srg != null) {
                        try {
                            Field f3 = clazz.getDeclaredField(srg);
                            f3.setAccessible(true);
                            return f3;
                        }
                        catch (NoSuchFieldException noSuchFieldException) {
                            // empty catch block
                        }
                    }
                    throw new RuntimeException("Field not found: " + mcpName + " in " + clazz.getName());
                }
            }
        });
        try {
            return (T)field.get(obj);
        }
        catch (Exception e) {
            throw new RuntimeException("Failed to get field: " + mcpName, e);
        }
    }

    public static void setField(Object obj, String mcpName, Object value) {
        if (obj == null) {
            throw new IllegalArgumentException("Object cannot be null");
        }
        Class<?> clazz = obj.getClass();
        String cacheKey = clazz.getName() + "." + mcpName;
        Field field = fieldCache.computeIfAbsent(cacheKey, k -> {
            try {
                Field f = clazz.getDeclaredField(mcpName);
                f.setAccessible(true);
                return f;
            }
            catch (NoSuchFieldException f) {
                try {
                    Field f2 = ObfuscationReflectionHelper.findField((Class)clazz, (String)mcpName);
                    f2.setAccessible(true);
                    return f2;
                }
                catch (Exception f2) {
                    String srg = MinecraftHelper.findSrgFieldName(clazz.getName(), mcpName);
                    if (srg != null) {
                        try {
                            Field f3 = clazz.getDeclaredField(srg);
                            f3.setAccessible(true);
                            return f3;
                        }
                        catch (NoSuchFieldException noSuchFieldException) {
                            // empty catch block
                        }
                    }
                    throw new RuntimeException("Field not found: " + mcpName + " in " + clazz.getName());
                }
            }
        });
        try {
            field.set(obj, value);
        }
        catch (Exception e) {
            throw new RuntimeException("Failed to set field: " + mcpName, e);
        }
    }

    public static <T> T invokeStaticMethod(Class<?> clazz, String mcpName, Object ... args) {
        Method method = MinecraftHelper.resolveCachedMethod(clazz, mcpName, args, true);
        try {
            return (T)method.invoke(null, args);
        }
        catch (Exception e) {
            throw new RuntimeException("Failed to invoke static method: " + mcpName, e);
        }
    }

    public static <T> T invokeMethod(Object obj, String mcpName, Object ... args) {
        if (obj == null) {
            throw new IllegalArgumentException("Object cannot be null");
        }
        Method method = MinecraftHelper.resolveCachedMethod(obj.getClass(), mcpName, args, false);
        try {
            return (T)method.invoke(obj, args);
        }
        catch (Exception e) {
            throw new RuntimeException("Failed to invoke method: " + mcpName, e);
        }
    }

    private static Method resolveCachedMethod(Class<?> clazz, String mcpName, Object[] args, boolean staticOnly) {
        if (args == null || MinecraftHelper.hasNull(args)) {
            return MinecraftHelper.resolveMethod(clazz, mcpName, args, staticOnly);
        }
        String cacheKey = MinecraftHelper.methodCacheKey(clazz, mcpName, args) + (staticOnly ? "#static" : "#instance");
        Method cached = methodCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        Method resolved = MinecraftHelper.resolveMethod(clazz, mcpName, args, staticOnly);
        methodCache.put(cacheKey, resolved);
        return resolved;
    }

    private static String methodCacheKey(Class<?> clazz, String name, Object[] args) {
        StringBuilder sb = new StringBuilder(clazz.getName()).append('#').append(name).append('(');
        if (args != null) {
            for (int i = 0; i < args.length; ++i) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(args[i] == null ? "?" : args[i].getClass().getName());
            }
        }
        return sb.append(')').toString();
    }

    private static Method resolveMethod(Class<?> clazz, String mcpName, Object[] args, boolean staticOnly) {
        if (mcpName == null || mcpName.isEmpty()) {
            throw new IllegalArgumentException("Method name must not be empty");
        }
        Object[] actualArgs = args != null ? args : new Object[0];
        if (!mappingLoaded) {
            MinecraftHelper.loadMappingsFromResource();
        }
        String srgName = MinecraftHelper.findSrgMethodName(clazz.getName(), mcpName);
        ArrayList<Method> candidates = new ArrayList<Method>();
        for (Class<?> current = clazz; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.getParameterCount() != actualArgs.length) continue;
                if (Modifier.isStatic(method.getModifiers()) != staticOnly) continue;
                if (!method.getName().equals(mcpName) && (srgName == null || !method.getName().equals(srgName))) continue;
                method.setAccessible(true);
                candidates.add(method);
            }
        }
        if (candidates.isEmpty()) {
            throw new RuntimeException("Method not found: " + mcpName + " with " + actualArgs.length + " argument(s) in " + clazz.getName() + " args=" + MinecraftHelper.describeArgTypes(actualArgs));
        }
        Method best = null;
        int bestScore = -1;
        for (Method candidate : candidates) {
            int score = MinecraftHelper.scoreParameters(candidate.getParameterTypes(), actualArgs);
            if (score < 0 || score <= bestScore) continue;
            bestScore = score;
            best = candidate;
        }
        if (best == null) {
            throw new RuntimeException("No compatible overload for: " + mcpName + " in " + clazz.getName() + " args=" + MinecraftHelper.describeArgTypes(actualArgs));
        }
        if (candidates.size() > 1 && MinecraftHelper.hasNull(actualArgs)) {
            LOGGER.warn("Ambiguous null argument for {} in {}: picked {} among {} candidates", (Object)mcpName, (Object)clazz.getName(), (Object)best, (Object)candidates.size());
        }
        return best;
    }

    private static int scoreParameters(Class<?>[] paramTypes, Object[] args) {
        int score = 0;
        for (int i = 0; i < paramTypes.length; ++i) {
            Class<?> param = paramTypes[i];
            Object arg = args[i];
            if (arg == null) {
                ++score;
                continue;
            }
            Class<?> argType = arg.getClass();
            if (param.equals(argType)) {
                score += 4;
                continue;
            }
            if (param.isAssignableFrom(argType)) {
                score += 3;
                continue;
            }
            if (MinecraftHelper.isUnboxingCompatible(param, argType)) {
                score += 2;
                continue;
            }
            return -1;
        }
        return score;
    }

    private static boolean isUnboxingCompatible(Class<?> param, Class<?> argType) {
        if (!param.isPrimitive()) {
            return false;
        }
        if (param == Integer.TYPE) {
            return argType == Integer.class;
        }
        if (param == Long.TYPE) {
            return argType == Long.class;
        }
        if (param == Float.TYPE) {
            return argType == Float.class;
        }
        if (param == Double.TYPE) {
            return argType == Double.class;
        }
        if (param == Boolean.TYPE) {
            return argType == Boolean.class;
        }
        if (param == Byte.TYPE) {
            return argType == Byte.class;
        }
        if (param == Short.TYPE) {
            return argType == Short.class;
        }
        if (param == Character.TYPE) {
            return argType == Character.class;
        }
        return false;
    }

    private static boolean hasNull(Object[] args) {
        if (args == null) {
            return false;
        }
        for (Object arg : args) {
            if (arg == null) {
                return true;
            }
        }
        return false;
    }

    private static String describeArgTypes(Object[] args) {
        if (args == null || args.length == 0) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < args.length; ++i) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(args[i] == null ? "null" : args[i].getClass().getName());
        }
        return sb.append(']').toString();
    }

    public static Class<?>[] getParameterTypes(Object ... args) {
        if (args == null || args.length == 0) {
            return new Class[0];
        }
        Class[] types = new Class[args.length];
        for (int i = 0; i < args.length; ++i) {
            types[i] = args[i] != null ? args[i].getClass() : Object.class;
        }
        return types;
    }

    public static void clearCache() {
        fieldCache.clear();
        methodCache.clear();
        fieldMappings.clear();
        methodMappings.clear();
        classNameMappings.clear();
        methodReturnTypes.clear();
        methodMappingsByDescriptor.clear();
        methodReturnTypesByDescriptor.clear();
        superClassCache.clear();
        mappingLoaded = false;
        mappingFilePath = null;
        srgRuntime = null;
        LOGGER.info("MinecraftHelper cache cleared");
    }

    public static <T> T getStaticField(String className, String fieldName) {
        try {
            return MinecraftHelper.getStaticField(Class.forName(className), fieldName);
        }
        catch (ClassNotFoundException e) {
            throw new RuntimeException("Class not found: " + className, e);
        }
    }

    public static <T> T invokeStaticMethod(String className, String methodName, Object ... args) {
        try {
            return MinecraftHelper.invokeStaticMethod(Class.forName(className), methodName, args);
        }
        catch (ClassNotFoundException e) {
            throw new RuntimeException("Class not found: " + className, e);
        }
    }
}

