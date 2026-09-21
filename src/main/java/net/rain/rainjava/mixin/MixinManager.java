/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.gson.Gson
 *  com.google.gson.GsonBuilder
 *  com.google.gson.JsonArray
 *  com.google.gson.JsonElement
 *  com.google.gson.JsonObject
 *  org.apache.logging.log4j.Logger
 */
package net.rain.rainjava.mixin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.rain.rainjava.RainJava;
import net.rain.rainjava.java.CompiledClass;
import net.rain.rainjava.java.JavaSourceCompiler;
import net.rain.rainjava.mixin.refmap.RefMapGenerator;
import org.apache.logging.log4j.Logger;

public class MixinManager {
    private static final Logger LOGGER = RainJava.LOGGER;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String MIXIN_PACKAGE = "rainjava.mixins";
    private static final String DYNAMIC_MIXIN_CONFIG = "rainjava.mixins.json";
    private static final String REFMAP_NAME = "rainjava.refmap.json";
    private static final String COMPILE_STATE_FILE = "compile_state.json";
    private final Path mixinSourceDir;
    private final Path mixinOutputDir;
    private final Path mixinConfigDir;
    private final Path mixinConfigFile;
    private final Path refmapFile;
    private final Path compileStateFile;
    private final Path mappingFile;
    private final Map<String, MixinInfo> discoveredMixins = new LinkedHashMap<String, MixinInfo>();
    private final RefMapGenerator refMapGenerator;
    private boolean mixinsCompiled = false;

    public MixinManager(Path gameDir) {
        this.mixinSourceDir = gameDir.resolve("RainJava/mixins");
        this.mixinConfigDir = gameDir.resolve(".rain_mixin");
        this.mixinOutputDir = this.mixinConfigDir.resolve("rainjava/mixins");
        this.mixinConfigFile = this.mixinConfigDir.resolve(DYNAMIC_MIXIN_CONFIG);
        this.refmapFile = this.mixinConfigDir.resolve(REFMAP_NAME);
        this.compileStateFile = this.mixinConfigDir.resolve(COMPILE_STATE_FILE);
        this.mappingFile = null;
        this.refMapGenerator = new RefMapGenerator(LOGGER);
        try {
            Files.createDirectories(this.mixinSourceDir, new FileAttribute[0]);
            Files.createDirectories(this.mixinOutputDir, new FileAttribute[0]);
            LOGGER.info("========================================");
            LOGGER.info("RainMixin Manager Initialized");
            LOGGER.info("========================================");
            LOGGER.info("Source Dir:  {}", (Object)this.mixinSourceDir);
            LOGGER.info("Output Dir:  {}", (Object)this.mixinOutputDir);
            LOGGER.info("Config File: {}", (Object)this.mixinConfigFile);
            LOGGER.info("RefMap File: {}", (Object)this.refmapFile);
            LOGGER.info("Package:     {}", (Object)MIXIN_PACKAGE);
            LOGGER.info("========================================");
        }
        catch (IOException e) {
            LOGGER.error("Failed to create mixin directories", (Throwable)e);
        }
    }

    public boolean needsRecompile() {
        LOGGER.info("Checking if recompilation is needed...");
        if (!Files.exists(this.mixinSourceDir, new LinkOption[0])) {
            LOGGER.info("Source directory does not exist, no compilation needed");
            return false;
        }
        Map<String, FileSnapshot> currentFiles = this.scanCurrentFiles();
        if (currentFiles.isEmpty()) {
            LOGGER.info("No mixin source files found");
            if (Files.exists(this.mixinConfigFile, new LinkOption[0]) || Files.exists(this.refmapFile, new LinkOption[0])) {
                LOGGER.info("No source files but compiled artifacts exist, will clean up");
                return true;
            }
            return false;
        }
        CompileState lastState = this.loadCompileState();
        if (lastState == null || lastState.files == null || lastState.files.isEmpty()) {
            LOGGER.info("No previous compile state found (first run), compilation needed");
            return true;
        }
        if (currentFiles.size() != lastState.files.size()) {
            LOGGER.info("File count changed: {} -> {}", (Object)lastState.files.size(), (Object)currentFiles.size());
            return true;
        }
        for (Map.Entry<String, FileSnapshot> entry : currentFiles.entrySet()) {
            String path = entry.getKey();
            FileSnapshot currentSnapshot = entry.getValue();
            FileSnapshot lastSnapshot = lastState.files.get(path);
            if (lastSnapshot == null) {
                LOGGER.info("New file detected: {}", (Object)path);
                return true;
            }
            if (currentSnapshot.lastModified != lastSnapshot.lastModified) {
                LOGGER.info("File modified (timestamp changed): {}", (Object)path);
                LOGGER.info("  Old: {}, New: {}", (Object)lastSnapshot.lastModified, (Object)currentSnapshot.lastModified);
                return true;
            }
            if (currentSnapshot.size == lastSnapshot.size) continue;
            LOGGER.info("File modified (size changed): {}", (Object)path);
            LOGGER.info("  Old: {} bytes, New: {} bytes", (Object)lastSnapshot.size, (Object)currentSnapshot.size);
            return true;
        }
        for (String oldPath : lastState.files.keySet()) {
            if (currentFiles.containsKey(oldPath)) continue;
            LOGGER.info("File deleted: {}", (Object)oldPath);
            return true;
        }
        LOGGER.info("\u2713 No changes detected, compilation not needed");
        return false;
    }

    private Map<String, FileSnapshot> scanCurrentFiles() {
        HashMap<String, FileSnapshot> snapshots = new HashMap<String, FileSnapshot>();
        try {
            if (!Files.exists(this.mixinSourceDir, new LinkOption[0])) {
                return snapshots;
            }
            Files.walk(this.mixinSourceDir, new FileVisitOption[0]).filter(p -> p.toString().endsWith(".java")).forEach(p -> {
                try {
                    FileSnapshot snapshot = new FileSnapshot();
                    snapshot.path = p.toString();
                    snapshot.lastModified = Files.getLastModifiedTime(p, new LinkOption[0]).toMillis();
                    snapshot.size = Files.size(p);
                    snapshots.put(snapshot.path, snapshot);
                    LOGGER.debug("Scanned file: {} (modified: {}, size: {} bytes)", (Object)p.getFileName(), (Object)snapshot.lastModified, (Object)snapshot.size);
                }
                catch (IOException e) {
                    LOGGER.warn("Failed to scan file: {}", p, (Object)e);
                }
            });
        }
        catch (IOException e) {
            LOGGER.error("Failed to scan mixin source directory", (Throwable)e);
        }
        return snapshots;
    }

    private CompileState loadCompileState() {
        if (!Files.exists(this.compileStateFile, new LinkOption[0])) {
            LOGGER.debug("No compile state file found at: {}", (Object)this.compileStateFile);
            return null;
        }
        try {
            String content = Files.readString(this.compileStateFile);
            CompileState state = (CompileState)GSON.fromJson(content, CompileState.class);
            LOGGER.debug("Loaded compile state from: {}", (Object)this.compileStateFile);
            LOGGER.debug("  Compile time: {}", (Object)state.compileTime);
            LOGGER.debug("  Tracked files: {}", (Object)(state.files != null ? state.files.size() : 0));
            return state;
        }
        catch (Exception e) {
            LOGGER.warn("Failed to load compile state, will treat as first run", (Throwable)e);
            return null;
        }
    }

    private void saveCompileState() {
        try {
            Map<String, FileSnapshot> currentFiles = this.scanCurrentFiles();
            CompileState state = new CompileState();
            state.compileTime = System.currentTimeMillis();
            state.files = currentFiles;
            String json = GSON.toJson((Object)state);
            Files.writeString(this.compileStateFile, (CharSequence)json, new OpenOption[0]);
            LOGGER.info("\u2713 Compile state saved");
            LOGGER.debug("  Tracked {} files", (Object)currentFiles.size());
            LOGGER.debug("  State file: {}", (Object)this.compileStateFile);
        }
        catch (Exception e) {
            LOGGER.error("Failed to save compile state", (Throwable)e);
        }
    }

    public void scanMixinSources() {
        if (!Files.exists(this.mixinSourceDir, new LinkOption[0])) {
            LOGGER.info("Mixin source directory does not exist: {}", (Object)this.mixinSourceDir);
            return;
        }
        this.discoveredMixins.clear();
        try {
            Files.walk(this.mixinSourceDir, new FileVisitOption[0]).filter(p -> p.toString().endsWith(".java")).forEach(this::analyzeMixinFile);
        }
        catch (IOException e) {
            LOGGER.error("Failed to scan mixin sources", (Throwable)e);
        }
        if (this.discoveredMixins.isEmpty()) {
            LOGGER.info("No Mixin files found in {}", (Object)this.mixinSourceDir);
        } else {
            LOGGER.info("Found {} Mixin file(s):", (Object)this.discoveredMixins.size());
            this.discoveredMixins.forEach((name, info) -> LOGGER.info("  - {} ({}) -> {}", name, (Object)info.side, (Object)info.targetClass));
        }
    }

    private void analyzeMixinFile(Path file) {
        try {
            String content = Files.readString(file);
            if (!content.contains("@Mixin")) {
                LOGGER.debug("File {} does not contain @Mixin annotation", (Object)file.getFileName());
                return;
            }
            String className = this.extractClassName(content);
            if (className == null) {
                LOGGER.warn("Could not extract class name from: {}", (Object)file);
                return;
            }
            String targetClass = this.extractTargetClassFromSource(content);
            if (targetClass == null) {
                LOGGER.warn("Could not extract target class from @Mixin in: {}", (Object)file.getFileName());
                return;
            }
            Side side = this.determineSide(file, content);
            MixinInfo info = new MixinInfo();
            info.sourceFile = file;
            info.className = className;
            info.fullClassName = "rainjava.mixins." + className;
            info.targetClass = targetClass;
            info.side = side;
            this.discoveredMixins.put(className, info);
            LOGGER.debug("Discovered mixin: {} -> {} (side: {})", (Object)className, (Object)targetClass, (Object)side);
        }
        catch (IOException e) {
            LOGGER.error("Failed to read mixin file: {}", (Object)file, (Object)e);
        }
    }

    private String extractTargetClassFromSource(String source) {
        Pattern pattern1 = Pattern.compile("@Mixin\\s*\\(\\s*([A-Za-z][A-Za-z0-9_.]*)\\.class\\s*\\)");
        Matcher matcher1 = pattern1.matcher(source);
        if (matcher1.find()) {
            String className = matcher1.group(1);
            LOGGER.debug("  Found target via .class: {}", (Object)className);
            return className.replace('.', '/');
        }
        Pattern pattern2 = Pattern.compile("@Mixin\\s*\\(\\s*value\\s*=\\s*([A-Za-z][A-Za-z0-9_.]*)\\.class");
        Matcher matcher2 = pattern2.matcher(source);
        if (matcher2.find()) {
            String className = matcher2.group(1);
            LOGGER.debug("  Found target via value: {}", (Object)className);
            return className.replace('.', '/');
        }
        Pattern pattern3 = Pattern.compile("@Mixin\\s*\\(\\s*targets?\\s*=\\s*\"([^\"]+)\"");
        Matcher matcher3 = pattern3.matcher(source);
        if (matcher3.find()) {
            String className = matcher3.group(1);
            LOGGER.debug("  Found target via targets string: {}", (Object)className);
            return className.replace('.', '/');
        }
        Pattern pattern4 = Pattern.compile("@Mixin\\s*\\(\\s*\"([^\"]+)\"\\s*\\)");
        Matcher matcher4 = pattern4.matcher(source);
        if (matcher4.find()) {
            String className = matcher4.group(1);
            LOGGER.debug("  Found target via string: {}", (Object)className);
            return className.replace('.', '/');
        }
        return null;
    }

    private String extractClassName(String source) {
        String[] keywords;
        String cleanSource = source.replaceAll("//.*", "").replaceAll("/\\*.*?\\*/", "");
        for (String keyword : keywords = new String[]{"class ", "interface "}) {
            String after;
            String[] tokens;
            int idx = cleanSource.indexOf(keyword);
            if (idx < 0 || (tokens = (after = cleanSource.substring(idx + keyword.length())).split("[\\s{<]")).length < 1 || tokens[0].trim().isEmpty()) continue;
            return tokens[0].trim();
        }
        return null;
    }

    private Side determineSide(Path file, String content) {
        String pathStr = file.toString().toLowerCase();
        String contentLower = content.toLowerCase();
        if (pathStr.contains("/client/") || pathStr.contains("\\client\\") || contentLower.contains("@onlyin(dist.client)") || contentLower.contains("minecraft.class") || contentLower.contains("localplayer.class") || contentLower.contains("clientlevel.class")) {
            return Side.CLIENT;
        }
        if (pathStr.contains("/server/") || pathStr.contains("\\server\\") || contentLower.contains("@onlyin(dist.dedicated_server)") || contentLower.contains("serverplayer.class") || contentLower.contains("serverlevel.class")) {
            return Side.SERVER;
        }
        return Side.COMMON;
    }

    public void compileMixins(JavaSourceCompiler compiler) {
        if (this.discoveredMixins.isEmpty()) {
            LOGGER.info("No mixins to compile");
            return;
        }
        LOGGER.info("========================================");
        LOGGER.info("Compiling {} Mixin class(es)...", (Object)this.discoveredMixins.size());
        LOGGER.info("========================================");
        int success = 0;
        int failed = 0;
        try {
            if (Files.exists(this.mixinOutputDir, new LinkOption[0])) {
                LOGGER.info("Cleaning output directory...");
                Files.walk(this.mixinOutputDir, new FileVisitOption[0]).sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        if (!path.equals(this.mixinOutputDir)) {
                            Files.deleteIfExists(path);
                        }
                    }
                    catch (IOException e) {
                        LOGGER.warn("Failed to delete: {}", path);
                    }
                });
            }
            Files.createDirectories(this.mixinOutputDir, new FileAttribute[0]);
        }
        catch (IOException e) {
            LOGGER.error("Failed to clean output directory", (Throwable)e);
        }
        for (MixinInfo info : this.discoveredMixins.values()) {
            try {
                LOGGER.info("Compiling: {} ...", (Object)info.className);
                CompiledClass compiled = compiler.compile(info.sourceFile);
                Path classFile = this.mixinOutputDir.resolve(info.className + ".class");
                Files.write(classFile, compiled.bytecode, new OpenOption[0]);
                LOGGER.info("  \u2713 Compiled: {} ({} bytes)", (Object)info.className, (Object)compiled.bytecode.length);
                ++success;
            }
            catch (Exception e) {
                LOGGER.error("  \u2717 Failed to compile: {}", (Object)info.className, (Object)e);
                ++failed;
            }
        }
        LOGGER.info("========================================");
        LOGGER.info("Compilation complete: {} success, {} failed", (Object)success, (Object)failed);
        LOGGER.info("========================================");
        if (success > 0) {
            this.mixinsCompiled = true;
        }
    }

    public void generateRefMap() {
        if (this.discoveredMixins.isEmpty()) {
            LOGGER.info("No mixins to generate RefMap for");
            return;
        }
        LOGGER.info("========================================");
        LOGGER.info("Generating RefMap");
        LOGGER.info("========================================");
        this.refMapGenerator.loadMappings(null);
        for (MixinInfo info : this.discoveredMixins.values()) {
            Path classFile = this.mixinOutputDir.resolve(info.className + ".class");
            if (!Files.exists(classFile, new LinkOption[0])) continue;
            this.refMapGenerator.analyzeMixinWithSource(info.className, info.sourceFile, classFile, info.targetClass);
        }
        this.refMapGenerator.generateRefMap(this.refmapFile, REFMAP_NAME);
        Map<String, Integer> stats = this.refMapGenerator.getStatistics();
        LOGGER.info("RefMap Statistics:");
        LOGGER.info("  Classes: {}", (Object)stats.get("classes"));
        LOGGER.info("  Methods: {}", (Object)stats.get("methods"));
        LOGGER.info("  Fields: {}", (Object)stats.get("fields"));
        LOGGER.info("  Mixin mappings: {}", (Object)stats.get("totalMappings"));
        LOGGER.info("========================================");
    }

    public void generateMixinConfig() {
        if (this.discoveredMixins.isEmpty()) {
            LOGGER.info("No mixins discovered, skipping config generation");
            this.cleanupConfigFiles();
            return;
        }
        LOGGER.info("========================================");
        LOGGER.info("Generating Mixin Configuration");
        LOGGER.info("========================================");
        ArrayList<String> commonMixins = new ArrayList<String>();
        ArrayList<String> clientMixins = new ArrayList<String>();
        ArrayList<String> serverMixins = new ArrayList<String>();
        for (MixinInfo info : this.discoveredMixins.values()) {
            switch (info.side) {
                case CLIENT: {
                    clientMixins.add(info.className);
                    break;
                }
                case SERVER: {
                    serverMixins.add(info.className);
                    break;
                }
                case COMMON: {
                    commonMixins.add(info.className);
                }
            }
        }
        JsonObject config = new JsonObject();
        config.addProperty("required", Boolean.valueOf(true));
        config.addProperty("minVersion", "0.8");
        config.addProperty("package", MIXIN_PACKAGE);
        config.addProperty("compatibilityLevel", "JAVA_17");
        config.addProperty("refmap", REFMAP_NAME);
        if (!commonMixins.isEmpty()) {
            JsonArray arr = new JsonArray();
            commonMixins.forEach(arg_0 -> ((JsonArray)arr).add(arg_0));
            config.add("mixins", (JsonElement)arr);
            LOGGER.info("  Common mixins: {}", commonMixins);
        }
        if (!clientMixins.isEmpty()) {
            JsonArray arr = new JsonArray();
            clientMixins.forEach(arg_0 -> ((JsonArray)arr).add(arg_0));
            config.add("client", (JsonElement)arr);
            LOGGER.info("  Client mixins: {}", clientMixins);
        }
        if (!serverMixins.isEmpty()) {
            JsonArray arr = new JsonArray();
            serverMixins.forEach(arg_0 -> ((JsonArray)arr).add(arg_0));
            config.add("server", (JsonElement)arr);
            LOGGER.info("  Server mixins: {}", serverMixins);
        }
        JsonObject injectors = new JsonObject();
        injectors.addProperty("defaultRequire", (Number)1);
        config.add("injectors", (JsonElement)injectors);
        try {
            String jsonContent = GSON.toJson((JsonElement)config);
            Files.writeString(this.mixinConfigFile, (CharSequence)jsonContent, new OpenOption[0]);
            LOGGER.info("========================================");
            LOGGER.info("\u2713 Mixin config generated:");
            LOGGER.info("  Location: {}", (Object)this.mixinConfigFile);
            LOGGER.info("  Package: {}", (Object)MIXIN_PACKAGE);
            LOGGER.info("  Total mixins: {}", (Object)this.discoveredMixins.size());
            LOGGER.info("========================================");
        }
        catch (IOException e) {
            LOGGER.error("Failed to write mixin config", (Throwable)e);
        }
    }

    private void cleanupConfigFiles() {
        try {
            if (Files.exists(this.mixinConfigFile, new LinkOption[0])) {
                Files.delete(this.mixinConfigFile);
                LOGGER.info("Deleted old mixin config file");
            }
            if (Files.exists(this.refmapFile, new LinkOption[0])) {
                Files.delete(this.refmapFile);
                LOGGER.info("Deleted old refmap file");
            }
        }
        catch (IOException e) {
            LOGGER.warn("Failed to delete config files", (Throwable)e);
        }
    }

    public void showRestartMessage() {
        if (this.mixinsCompiled) {
            LOGGER.warn("");
            LOGGER.warn("\u2554\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2557");
            LOGGER.warn("\u2551  \u26a0   MIXINS COMPILED SUCCESSFULLY  \u26a0    \u2551");
            LOGGER.warn("\u2560\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2563");
            LOGGER.warn("\u2551                                       \u2551");
            LOGGER.warn("\u2551  Mixins will be loaded on NEXT START \u2551");
            LOGGER.warn("\u2551                                       \u2551");
            LOGGER.warn("\u2551  Please RESTART the game now!        \u2551");
            LOGGER.warn("\u2551                                       \u2551");
            LOGGER.warn("\u255a\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u255d");
            LOGGER.warn("");
        }
    }

    public void validateConfiguration() {
        LOGGER.info("========================================");
        LOGGER.info("Validating Mixin Configuration");
        LOGGER.info("========================================");
        if (!Files.exists(this.mixinConfigFile, new LinkOption[0])) {
            LOGGER.warn("\u26a0  Config file does not exist: {}", (Object)this.mixinConfigFile);
            LOGGER.warn("  Mixins will not be loaded until next restart");
            return;
        }
        try {
            String pkg;
            String content = Files.readString(this.mixinConfigFile);
            JsonObject config = (JsonObject)GSON.fromJson(content, JsonObject.class);
            String string = pkg = config.has("package") ? config.get("package").getAsString() : "";
            if (pkg.isEmpty()) {
                LOGGER.error("\u2717 CRITICAL: Config file has empty package!");
                LOGGER.error("  This will prevent mixins from loading!");
                return;
            }
            int totalMixins = 0;
            if (config.has("mixins")) {
                totalMixins += config.getAsJsonArray("mixins").size();
            }
            if (config.has("client")) {
                totalMixins += config.getAsJsonArray("client").size();
            }
            if (config.has("server")) {
                totalMixins += config.getAsJsonArray("server").size();
            }
            LOGGER.info("\u2713 Config file is valid");
            LOGGER.info("  Package: {}", (Object)pkg);
            LOGGER.info("  Total mixins: {}", (Object)totalMixins);
            int classCount = 0;
            if (Files.exists(this.mixinOutputDir, new LinkOption[0])) {
                classCount = (int)Files.walk(this.mixinOutputDir, new FileVisitOption[0]).filter(p -> p.toString().endsWith(".class")).count();
            }
            LOGGER.info("  Class files: {}", (Object)classCount);
            if (Files.exists(this.refmapFile, new LinkOption[0])) {
                long size = Files.size(this.refmapFile);
                LOGGER.info("\u2713 RefMap exists ({} bytes)", (Object)size);
            } else {
                LOGGER.warn("\u26a0  RefMap file not found");
            }
            if (classCount != totalMixins) {
                LOGGER.warn("\u26a0  Warning: Mixin count mismatch!");
                LOGGER.warn("  Config: {} mixins", (Object)totalMixins);
                LOGGER.warn("  Files:  {} class files", (Object)classCount);
            } else {
                LOGGER.info("\u2713 All mixins have corresponding class files");
            }
        }
        catch (Exception e) {
            LOGGER.error("Failed to validate config", (Throwable)e);
        }
        LOGGER.info("========================================");
    }

    public void runFullWorkflow(JavaSourceCompiler compiler) {
        LOGGER.info("");
        LOGGER.info("\u2554\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2557");
        LOGGER.info("\u2551    RainMixin Manager Starting...     \u2551");
        LOGGER.info("\u255a\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u255d");
        LOGGER.info("");
        this.scanMixinSources();
        if (!this.needsRecompile()) {
            LOGGER.info("\u2713 No recompilation needed, skipping build process");
            this.validateConfiguration();
            return;
        }
        LOGGER.info("Changes detected, starting compilation...");
        this.compileMixins(compiler);
        this.generateRefMap();
        this.generateMixinConfig();
        this.saveCompileState();
        this.validateConfiguration();
        this.showRestartMessage();
    }

    public Path getMixinSourceDir() {
        return this.mixinSourceDir;
    }

    public Path getMixinOutputDir() {
        return this.mixinOutputDir;
    }

    public Path getMixinConfigFile() {
        return this.mixinConfigFile;
    }

    public Path getRefmapFile() {
        return this.refmapFile;
    }

    public boolean hasMixinsCompiled() {
        return this.mixinsCompiled;
    }

    public int getMixinCount() {
        return this.discoveredMixins.size();
    }

    private static class CompileState {
        long compileTime;
        Map<String, FileSnapshot> files;

        private CompileState() {
        }
    }

    private static class FileSnapshot {
        String path;
        long lastModified;
        long size;

        private FileSnapshot() {
        }
    }

    private static enum Side {
        COMMON,
        CLIENT,
        SERVER;

    }

    private static class MixinInfo {
        Path sourceFile;
        String className;
        String fullClassName;
        String targetClass;
        Side side;

        private MixinInfo() {
        }
    }
}

