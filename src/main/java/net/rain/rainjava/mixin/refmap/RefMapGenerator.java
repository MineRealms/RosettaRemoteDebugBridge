/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.gson.Gson
 *  com.google.gson.GsonBuilder
 *  com.google.gson.JsonElement
 *  com.google.gson.JsonObject
 *  org.apache.logging.log4j.Logger
 *  org.objectweb.asm.ClassReader
 *  org.objectweb.asm.ClassVisitor
 *  org.objectweb.asm.tree.AnnotationNode
 *  org.objectweb.asm.tree.ClassNode
 *  org.objectweb.asm.tree.FieldNode
 *  org.objectweb.asm.tree.MethodNode
 */
package net.rain.rainjava.mixin.refmap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

public class RefMapGenerator {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Logger logger;
    private final Map<String, String> classMap = new HashMap<String, String>();
    private final Map<String, String> methodMap = new HashMap<String, String>();
    private final Map<String, String> fieldMap = new HashMap<String, String>();
    private final Map<String, Map<String, String>> mixinMappings = new LinkedHashMap<String, Map<String, String>>();
    private Path mappingFilePath = null;
    private boolean mappingLoaded = false;
    private static final String[] DEFAULT_MAPPING_PATHS = new String[]{"config/forge/mappings.tsrg", "mappings/mappings.tsrg", ".gradle/caches/forge_gradle/mcp_mappings/mappings.tsrg", "joined.tsrg", "mappings/joined.tsrg"};
    private static final String[] DEFAULT_RESOURCE_PATHS = new String[]{"/assets/mappings/map/mappings.tsrg", "assets/mappings/map/mappings.tsrg", "/mappings.tsrg", "mappings.tsrg"};

    public RefMapGenerator(Logger logger) {
        this.logger = logger;
    }

    public void loadMappings(Path mappingFile) {
        if (this.mappingLoaded) {
            this.logger.info("Mappings already loaded");
            return;
        }
        this.loadMappingsFromResource();
        if (this.mappingLoaded) {
            return;
        }
        this.findAndLoadMappings();
        if (!this.mappingLoaded) {
            this.logger.warn("Failed to load mappings from any source. RefMap generation may be incomplete.");
        }
    }

    private void loadMappingsFromResource() {
        for (String resourcePath : DEFAULT_RESOURCE_PATHS) {
            this.logger.info("Trying to load mapping from resource: {}", (Object)resourcePath);
            try (InputStream is = this.getResourceAsStream(resourcePath);){
                if (is == null) continue;
                this.logger.info("Found mapping resource: {}", (Object)resourcePath);
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(is));){
                    this.parseTSRG2(reader);
                    this.mappingLoaded = true;
                    this.logger.info("\u2713 Loaded mappings from resource: {}", (Object)resourcePath);
                    this.logger.info("  Classes: {}, Methods: {}, Fields: {}", (Object)this.classMap.size(), (Object)this.methodMap.size(), (Object)this.fieldMap.size());
                }
                return;
            }
            catch (Exception e) {
                this.logger.info("Failed to load from resource {}: {}", (Object)resourcePath, (Object)e.getMessage());
            }
        }
    }

    private void loadMappingsFromFile(Path mappingFile) {
        if (!Files.exists(mappingFile, new LinkOption[0])) {
            this.logger.info("Mapping file not found: {}", (Object)mappingFile);
            return;
        }
        try (BufferedReader reader = Files.newBufferedReader(mappingFile);){
            this.logger.info("Loading mappings from file: {}", (Object)mappingFile);
            this.parseTSRG2(reader);
            this.mappingFilePath = mappingFile;
            this.mappingLoaded = true;
            this.logger.info("\u2713 Loaded mappings from file: {}", (Object)mappingFile);
            this.logger.info("  Classes: {}, Methods: {}, Fields: {}", (Object)this.classMap.size(), (Object)this.methodMap.size(), (Object)this.fieldMap.size());
        }
        catch (IOException e) {
            this.logger.error("Failed to load mappings from file: {}", (Object)mappingFile, (Object)e);
        }
    }

    private void findAndLoadMappings() {
        for (String pathStr : DEFAULT_MAPPING_PATHS) {
            Path path = Paths.get(pathStr, new String[0]);
            if (!Files.exists(path, new LinkOption[0])) continue;
            this.loadMappingsFromFile(path);
            if (!this.mappingLoaded) continue;
            return;
        }
        try {
            Files.list(Paths.get(".", new String[0])).filter(p -> p.toString().endsWith(".tsrg")).findFirst().ifPresent(this::loadMappingsFromFile);
        }
        catch (IOException e) {
            this.logger.info("Error searching for .tsrg files: {}", (Object)e.getMessage());
        }
    }

    private InputStream getResourceAsStream(String resourcePath) {
        InputStream is = RefMapGenerator.class.getResourceAsStream(resourcePath);
        if (is != null) {
            return is;
        }
        String path = resourcePath.startsWith("/") ? resourcePath.substring(1) : resourcePath;
        is = RefMapGenerator.class.getClassLoader().getResourceAsStream(path);
        if (is != null) {
            return is;
        }
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        if (contextClassLoader != null && (is = contextClassLoader.getResourceAsStream(path)) != null) {
            return is;
        }
        return null;
    }

    private void parseTSRG2(BufferedReader reader) throws IOException {
        String line;
        this.classMap.clear();
        this.methodMap.clear();
        this.fieldMap.clear();
        String currentClassObf = null;
        String currentClassSrg = null;
        boolean isFirstLine = true;
        while ((line = reader.readLine()) != null) {
            String originalLine = line;
            if ((line = line.trim()).isEmpty() || line.startsWith("#")) continue;
            if (isFirstLine && line.startsWith("tsrg2")) {
                this.logger.info("Found TSRG2 header: {}", (Object)line);
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
                String[] parts = line.split("\\s+");
                if (parts.length < 2) continue;
                currentClassObf = parts[0];
                currentClassSrg = parts[1];
                this.classMap.put(currentClassObf, currentClassSrg);
                continue;
            }
            if (currentClassObf == null || currentClassSrg == null || tabCount != 1) continue;
            this.parseMemberMapping(line, currentClassObf, currentClassSrg);
        }
    }

    private void parseMemberMapping(String line, String classObf, String classSrg) {
        String[] parts = line.split("\\s+");
        if (parts.length < 2) {
            return;
        }
        String nameObf = parts[0];
        String descriptor = parts[1];
        if (descriptor.startsWith("(")) {
            if (parts.length >= 3) {
                String nameSrg = parts[2];
                String key = classObf + "." + nameObf + descriptor;
                String value = this.convertToInternalName(classSrg) + "." + nameSrg + descriptor;
                this.methodMap.put(key, value);
            }
        } else {
            String nameSrg = descriptor;
            String key = classObf + "." + nameObf;
            String value = nameSrg;
            this.fieldMap.put(key, value);
        }
    }

    private String convertToInternalName(String className) {
        return className.replace('.', '/');
    }

    private String convertToJvmType(String internalName) {
        if (internalName.startsWith("L") && internalName.endsWith(";")) {
            return internalName;
        }
        return "L" + internalName + ";";
    }

    public void analyzeMixinWithSource(String mixinName, Path sourceFile, Path classFile, String targetClass) {
        this.logger.info("Analyzing mixin: {}", (Object)mixinName);
        this.logger.info("  Target class: {}", (Object)targetClass);
        try {
            String sourceCode = Files.readString(sourceFile);
            byte[] bytecode = Files.readAllBytes(classFile);
            ClassReader reader = new ClassReader(bytecode);
            ClassNode classNode = new ClassNode();
            reader.accept((ClassVisitor)classNode, 0);
            HashMap<String, String> mappings = new HashMap<String, String>();
            int fieldCount = this.analyzeMixinFieldsFromSource(sourceCode, classNode, targetClass, mappings);
            this.logger.info("  Found {} field references", (Object)fieldCount);
            int methodCount = this.analyzeMixinMethodsFromSource(sourceCode, classNode, targetClass, mappings);
            this.logger.info("  Found {} method references", (Object)methodCount);
            if (!mappings.isEmpty()) {
                this.mixinMappings.put(mixinName, mappings);
                this.logger.info("  \u2713 Extracted {} total references", (Object)mappings.size());
                if (!mappings.isEmpty()) {
                    Map.Entry example = mappings.entrySet().iterator().next();
                    this.logger.info("    Example: {} -> {}", example.getKey(), example.getValue());
                }
            } else {
                this.logger.info("  \u2139 No obfuscated references found (may use deobfuscated names)");
            }
        }
        catch (IOException e) {
            this.logger.error("Failed to analyze mixin: {}", (Object)mixinName, (Object)e);
        }
    }

    private int analyzeMixinFieldsFromSource(String sourceCode, ClassNode classNode, String targetClass, Map<String, String> mappings) {
        int count = 0;
        Pattern shadowPattern = Pattern.compile("@Shadow[^;]*\\s+(private|public|protected)?\\s+[\\w<>\\[\\]]+\\s+(\\w+)\\s*;");
        Matcher matcher = shadowPattern.matcher(sourceCode);
        block0: while (matcher.find()) {
            String fieldName = matcher.group(2);
            this.logger.debug("    Found @Shadow field in source: {}", (Object)fieldName);
            for (FieldNode field : classNode.fields) {
                if (!field.name.equals(fieldName)) continue;
                String mapped = this.findFieldMapping(targetClass, fieldName);
                if (mapped != null) {
                    String value = mapped + ":" + field.desc;
                    mappings.put(fieldName, value);
                    String fullKey = this.convertToJvmType(targetClass) + fieldName;
                    mappings.put(fullKey, value);
                    this.logger.debug("      Mapped: {} -> {}", (Object)fieldName, (Object)mapped);
                    ++count;
                    continue block0;
                }
                this.logger.debug("      No mapping found for: {}", (Object)fieldName);
                continue block0;
            }
        }
        return count;
    }

    private int analyzeMixinMethodsFromSource(String sourceCode, ClassNode classNode, String targetClass, Map<String, String> mappings) {
        int count = 0;
        Pattern injectPattern = Pattern.compile("@(?:Inject|Redirect|ModifyVariable|ModifyArg)\\s*\\([^)]*method\\s*=\\s*\"([^\"]+)\"");
        Matcher matcher = injectPattern.matcher(sourceCode);
        while (matcher.find()) {
            String methodReference = matcher.group(1);
            this.logger.debug("    Found method reference: {}", (Object)methodReference);
            if (!this.parseMethodReference(methodReference, targetClass, mappings)) continue;
            ++count;
        }
        for (MethodNode method : classNode.methods) {
            if (method.visibleAnnotations == null) continue;
            for (AnnotationNode anno : method.visibleAnnotations) {
                if (anno.desc == null || !anno.desc.contains("Overwrite")) continue;
                this.logger.debug("    Found @Overwrite method: {}", (Object)method.name);
                String mapped = this.findMethodMapping(targetClass, method.name, method.desc);
                if (mapped == null) continue;
                mappings.put(method.name, mapped);
                String fullKey = this.convertToJvmType(targetClass) + method.name + method.desc;
                mappings.put(fullKey, mapped);
                this.logger.debug("      Mapped: {} -> {}", (Object)method.name, (Object)mapped);
                ++count;
            }
        }
        return count;
    }

    private boolean parseMethodReference(String reference, String targetClass, Map<String, String> mappings) {
        String descriptor;
        String methodName;
        String className;
        String mapped;
        Pattern fullPattern;
        Matcher fullMatcher;
        Pattern simplePattern = Pattern.compile("([^(]+)(\\([^)]*\\).+)");
        Matcher simpleMatcher = simplePattern.matcher(reference);
        if (simpleMatcher.find()) {
            String descriptor2;
            String methodName2 = simpleMatcher.group(1);
            String mapped2 = this.findMethodMapping(targetClass, methodName2, descriptor2 = simpleMatcher.group(2));
            if (mapped2 != null) {
                mappings.put(methodName2, mapped2);
                String fullKey = this.convertToJvmType(targetClass) + methodName2 + descriptor2;
                mappings.put(fullKey, mapped2);
                this.logger.debug("      Mapped method: {} -> {}", (Object)methodName2, (Object)mapped2);
                return true;
            }
            this.logger.debug("      No mapping found for: {}{}", (Object)methodName2, (Object)descriptor2);
        }
        if ((fullMatcher = (fullPattern = Pattern.compile("L([^;]+);([^(]+)(\\([^)]*\\).+)")).matcher(reference)).find() && (mapped = this.findMethodMapping(className = fullMatcher.group(1), methodName = fullMatcher.group(2), descriptor = fullMatcher.group(3))) != null) {
            mappings.put(reference, mapped);
            mappings.put(methodName, mapped);
            this.logger.debug("      Mapped method: {} -> {}", (Object)methodName, (Object)mapped);
            return true;
        }
        return false;
    }

    private String findMethodMapping(String owner, String name, String desc) {
        String dotOwner = owner.replace('/', '.');
        String key = dotOwner + "." + name + desc;
        return this.methodMap.get(key);
    }

    private String findFieldMapping(String owner, String name) {
        String dotOwner = owner.replace('/', '.');
        String key = dotOwner + "." + name;
        return this.fieldMap.get(key);
    }

    public void generateRefMap(Path outputPath, String refmapName) {
        if (!this.mappingLoaded) {
            this.logger.warn("Mappings not loaded. Attempting to load automatically...");
            this.loadMappings(null);
        }
        this.logger.info("Generating RefMap: {}", (Object)refmapName);
        JsonObject root = new JsonObject();
        JsonObject mappings = new JsonObject();
        for (Map.Entry<String, Map<String, String>> entry : this.mixinMappings.entrySet()) {
            String mixinName = entry.getKey();
            Map<String, String> map = entry.getValue();
            if (map.isEmpty()) continue;
            JsonObject mixinObj = new JsonObject();
            for (Map.Entry<String, String> mapping : map.entrySet()) {
                mixinObj.addProperty(mapping.getKey(), mapping.getValue());
            }
            mappings.add(mixinName, (JsonElement)mixinObj);
        }
        root.add("mappings", (JsonElement)mappings);
        JsonObject data = new JsonObject();
        JsonObject searge = new JsonObject();
        for (Map.Entry entry : this.mixinMappings.entrySet()) {
            String mixinName = (String)entry.getKey();
            Map<String, String> mixinMap = (Map<String, String>)entry.getValue();
            if (mixinMap.isEmpty()) continue;
            JsonObject mixinObj = new JsonObject();
            for (Map.Entry mapping : mixinMap.entrySet()) {
                mixinObj.addProperty((String)mapping.getKey(), (String)mapping.getValue());
            }
            searge.add(mixinName, (JsonElement)mixinObj);
        }
        data.add("searge", (JsonElement)searge);
        root.add("data", (JsonElement)data);
        try {
            String firstMixin;
            Map<String, String> firstMap;
            String json = GSON.toJson((JsonElement)root);
            Files.writeString(outputPath, (CharSequence)json, new OpenOption[0]);
            this.logger.info("\u2713 RefMap generated successfully:");
            this.logger.info("  File: {}", (Object)outputPath);
            this.logger.info("  Mixins: {}", (Object)this.mixinMappings.size());
            int n = this.mixinMappings.values().stream().mapToInt(Map::size).sum();
            this.logger.info("  Total mappings: {}", (Object)n);
            if (!this.mixinMappings.isEmpty() && !(firstMap = this.mixinMappings.get(firstMixin = this.mixinMappings.keySet().iterator().next())).isEmpty()) {
                this.logger.info("Example mapping for {}:", (Object)firstMixin);
                Map.Entry<String, String> example = firstMap.entrySet().iterator().next();
                this.logger.info("  {} -> {}", (Object)example.getKey(), (Object)example.getValue());
            }
        }
        catch (IOException e) {
            this.logger.error("Failed to write RefMap", (Throwable)e);
        }
    }

    public Map<String, Integer> getStatistics() {
        HashMap<String, Integer> stats = new HashMap<String, Integer>();
        stats.put("classes", this.classMap.size());
        stats.put("methods", this.methodMap.size());
        stats.put("fields", this.fieldMap.size());
        stats.put("mixins", this.mixinMappings.size());
        stats.put("totalMappings", this.mixinMappings.values().stream().mapToInt(Map::size).sum());
        return stats;
    }

    public boolean isMappingLoaded() {
        return this.mappingLoaded;
    }

    public Path getMappingFilePath() {
        return this.mappingFilePath;
    }

    public void clear() {
        this.classMap.clear();
        this.methodMap.clear();
        this.fieldMap.clear();
        this.mixinMappings.clear();
        this.mappingFilePath = null;
        this.mappingLoaded = false;
    }

    public void reloadMappings(Path mappingFile) {
        this.clear();
        this.loadMappings(mappingFile);
    }
}

