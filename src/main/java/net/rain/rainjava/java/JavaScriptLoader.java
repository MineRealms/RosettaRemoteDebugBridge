/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext
 *  net.rain.repack.ecj.internal.compiler.tool.EclipseCompiler
 */
package net.rain.rainjava.java;

import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import javax.tools.JavaCompiler;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.rain.eventbus.RainEventSubscriber;
import net.rain.rainjava.RainJava;
import net.rain.rainjava.core.ScriptType;
import net.rain.rainjava.java.CompiledClass;
import net.rain.rainjava.java.DynamicClassLoader;
import net.rain.rainjava.java.JavaSourceCompiler;
import net.rain.rainjava.java.helper.RuntimeModuleOpener;
import net.rain.rainjava.java.transformer.McpToSrgTransformer;
import net.rain.rainjava.logging.RainJavaLogger;
import net.rain.rainjava.logging.ScriptErrorCollector;
import net.rain.repack.ecj.internal.compiler.tool.EclipseCompiler;

public class JavaScriptLoader {
    private final ScriptType scriptType;
    private final RainJavaLogger logger;
    private final JavaSourceCompiler compiler;
    private final DynamicClassLoader classLoader;
    private final List<Class<?>> loadedClasses;
    private final McpToSrgTransformer mcpTransformer;

    public JavaScriptLoader(ScriptType scriptType) {
        this.scriptType = scriptType;
        this.logger = new RainJavaLogger(scriptType);
        RuntimeModuleOpener.openMixinModules();
        EclipseCompiler systemCompiler = null;
        try {
            systemCompiler = new EclipseCompiler();
            this.logger.info("Using Eclipse JDT compiler", new Object[0]);
        }
        catch (Exception e) {
            this.logger.error("Failed to initialize Eclipse JDT compiler: {}", e.getMessage(), e);
        }
        if (systemCompiler == null) {
            this.logger.error("No Java compiler available! Dynamic Java compilation is disabled.", new Object[0]);
            this.compiler = null;
            this.classLoader = null;
            this.loadedClasses = new ArrayList();
            this.mcpTransformer = null;
            return;
        }
        try {
            this.compiler = new JavaSourceCompiler((JavaCompiler)systemCompiler);
            this.classLoader = new DynamicClassLoader(Thread.currentThread().getContextClassLoader());
            this.loadedClasses = new ArrayList();
            this.mcpTransformer = new McpToSrgTransformer();
            this.logger.info("MCP to SRG transformer initialized", new Object[0]);
            this.logger.info("Java script loader initialized for: {}", new Object[]{scriptType});
        }
        catch (Exception e) {
            this.logger.error("Failed to initialize Java script loader: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    public void processMixins() {
    }

    public void loadJavaScripts(Path directory) {
        if (this.compiler == null) {
            this.logger.warn("Compiler not available, skipping script loading", new Object[0]);
            return;
        }
        if (!Files.exists(directory, new LinkOption[0])) {
            this.logger.info("Scripts directory does not exist: {}", directory);
            return;
        }
        ArrayList<Path> javaFiles = new ArrayList<Path>();
        try (Stream<Path> paths = Files.walk(directory, new FileVisitOption[0]);){
            paths.filter(path -> {
                String p = path.toString();
                boolean isMixin = p.contains("mixins" + File.separator) || p.contains("mixins/");
                boolean isReplace = p.contains("replace" + File.separator) || p.contains("replace/");
                return p.endsWith(".java") && !isMixin && !isReplace;
            }).forEach(javaFiles::add);
        }
        catch (Exception e) {
            this.logger.error("Failed to scan scripts directory {}: {}", directory, e.getMessage(), e);
            ScriptErrorCollector.addFromThrowable(this.scriptType, directory.toString(), e);
            return;
        }
        if (javaFiles.isEmpty()) {
            this.logger.info("No Java script files found in {}", directory);
            return;
        }
        this.logger.info("Found {} file(s) to compile in {}", javaFiles.size(), directory);
        int success = 0;
        for (Path file : javaFiles) {
            if (!this.loadJavaFileWithTransform(file)) continue;
            ++success;
        }
        this.logger.info("Compiled {}/{} file(s) successfully", success, javaFiles.size());
        if (!this.loadedClasses.isEmpty()) {
            this.logger.info("Processing {} loaded class(es)...", this.loadedClasses.size());
            this.processLoadedClasses();
        }
    }

    private boolean loadJavaFileWithTransform(Path file) {
        String fileName = file.getFileName().toString();
        try {
            CompiledClass compiled;
            String source;
            this.logger.info("Processing: {}", fileName);
            Path absPath = this.resolveFilePath(file);
            if (!Files.exists(absPath, new LinkOption[0])) {
                this.logger.error("File not found: {}", absPath);
                ScriptErrorCollector.addError(this.scriptType, "File not found: " + String.valueOf(absPath), fileName, -1L);
                return false;
            }
            String originalSource = Files.readString(absPath);
            try {
                source = this.mcpTransformer.transformSource(originalSource, fileName);
            }
            catch (Exception e) {
                this.logger.warn("MCP->SRG transform failed for {}, using original source: {}", fileName, e.getMessage());
                source = originalSource;
            }
            String className = this.extractClassName(source, fileName);
            if (className == null || className.isBlank()) {
                this.logger.error("Could not extract class name from: {}", fileName);
                ScriptErrorCollector.addError(this.scriptType, "Could not extract class name", fileName, -1L);
                return false;
            }
            this.logger.info("  Class: {}", className);
            try {
                compiled = this.compiler.compileFromString(className, source);
            }
            catch (Exception e) {
                String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getName();
                RainJavaLogger.printCompilerOutput(this.scriptType, msg);
                JavaScriptLoader.parseAndCollectErrors(this.scriptType, fileName, msg);
                return false;
            }
            if (compiled == null || compiled.bytecode == null || compiled.bytecode.length == 0) {
                this.logger.error("  Compilation produced no bytecode for: {}", className);
                ScriptErrorCollector.addError(this.scriptType, "Compilation produced no bytecode", fileName, -1L);
                return false;
            }
            this.classLoader.addCompiledClass(compiled.className, compiled.bytecode);
            Class<?> clazz = this.classLoader.loadClass(compiled.className);
            this.loadedClasses.add(clazz);
            this.logger.info("  Loaded: {}", compiled.className);
            return true;
        }
        catch (Exception e) {
            this.logger.error("  FAIL: {}: {}", fileName, e.getMessage(), e);
            ScriptErrorCollector.addFromThrowable(this.scriptType, fileName, e);
            return false;
        }
    }

    private static void parseAndCollectErrors(ScriptType type, String fileName, String compilerOutput) {
        if (compilerOutput == null) {
            ScriptErrorCollector.addError(type, "Unknown compilation error", fileName, -1L);
            return;
        }
        String[] lines = compilerOutput.split("\n");
        boolean found = false;
        for (String line : lines) {
            if (!(line = line.trim()).startsWith("Line ") || !line.contains(":")) continue;
            try {
                int colon = line.indexOf(58);
                long lineNum = Long.parseLong(line.substring(5, colon).trim());
                String msg = line.substring(colon + 1).trim();
                ScriptErrorCollector.addError(type, msg, fileName, lineNum);
                found = true;
            }
            catch (NumberFormatException ignored) {
                ScriptErrorCollector.addError(type, line, fileName, -1L);
                found = true;
            }
        }
        if (!found) {
            ScriptErrorCollector.addError(type, compilerOutput, fileName, -1L);
        }
    }

    private void processLoadedClasses() {
        for (Class<?> clazz : this.loadedClasses) {
            this.processRainEventSubscriber(clazz);
            try {
                this.executeClass(clazz);
            }
            catch (Exception e) {
                this.logger.error("Failed to execute {}: {}", clazz.getName(), e.getMessage(), e);
                ScriptErrorCollector.addFromThrowable(this.scriptType, clazz.getSimpleName() + ".java", e);
            }
        }
    }

    private void processRainEventSubscriber(Class<?> clazz) {
        RainEventSubscriber annotation = clazz.getAnnotation(RainEventSubscriber.class);
        if (annotation == null) {
            return;
        }
        try {
            RainJava.EVENT_BUS.register(clazz);
            this.logger.info("@RainEventSubscriber registered: {} -> {} bus", clazz.getSimpleName(), annotation.bus().name());
        }
        catch (Exception e) {
            this.logger.error("Failed to register @RainEventSubscriber for {}: {}", clazz.getSimpleName(), e.getMessage(), e);
            ScriptErrorCollector.addFromThrowable(this.scriptType, clazz.getSimpleName() + ".java", e);
        }
    }

    private void executeClass(Class<?> clazz) {
        try {
            Method initMethod = this.findInitMethod(clazz);
            if (initMethod != null) {
                this.logger.info("Executing {}() in {}", initMethod.getName(), clazz.getSimpleName());
                initMethod.invoke(null, new Object[0]);
                return;
            }
            Method fmlMethod = this.findInitMethodWithFML(clazz);
            if (fmlMethod != null) {
                this.logger.info("Executing {}(FMLContext) in {}", fmlMethod.getName(), clazz.getSimpleName());
                fmlMethod.invoke(null, FMLJavaModLoadingContext.get());
                return;
            }
            if (!Modifier.isAbstract(clazz.getModifiers()) && !clazz.isInterface()) {
                try {
                    clazz.getDeclaredConstructor(new Class[0]).newInstance(new Object[0]);
                    this.logger.info("Instantiated: {}", clazz.getSimpleName());
                }
                catch (NoSuchMethodException e) {
                    this.logger.info("No init method or default constructor found in {}", clazz.getSimpleName());
                }
            }
        }
        catch (Exception e) {
            this.logger.error("Error executing {}: {}", clazz.getName(), e.getMessage(), e);
        }
    }

    private Method findInitMethod(Class<?> clazz) {
        for (String name : new String[]{"init", "initialize", "onLoad", "load", "register"}) {
            try {
                Method m = clazz.getDeclaredMethod(name, new Class[0]);
                if (!Modifier.isStatic(m.getModifiers()) || !Modifier.isPublic(m.getModifiers()) || m.getParameterCount() != 0) continue;
                return m;
            }
            catch (NoSuchMethodException noSuchMethodException) {
                // empty catch block
            }
        }
        return null;
    }

    private Method findInitMethodWithFML(Class<?> clazz) {
        for (String name : new String[]{"init", "initialize", "onLoad", "load", "register"}) {
            try {
                Method m = clazz.getDeclaredMethod(name, FMLJavaModLoadingContext.class);
                if (!Modifier.isStatic(m.getModifiers()) || !Modifier.isPublic(m.getModifiers())) continue;
                return m;
            }
            catch (NoSuchMethodException noSuchMethodException) {
                // empty catch block
            }
        }
        return null;
    }

    private String extractClassName(String source, String fileName) {
        try {
            int semi;
            String pkg = "";
            String cls = "";
            int pi = source.indexOf("package ");
            if (pi != -1 && (semi = source.indexOf(59, pi)) > pi) {
                pkg = source.substring(pi + 8, semi).trim();
            }
            for (String kw : new String[]{"public class ", "class ", "public interface ", "interface "}) {
                int ci = source.indexOf(kw);
                if (ci == -1) continue;
                int si = ci + kw.length();
                int end = source.length();
                for (char stop : new char[]{' ', '{', '<', '\n', '\r'}) {
                    int idx = source.indexOf(stop, si);
                    if (idx <= si) continue;
                    end = Math.min(end, idx);
                }
                cls = source.substring(si, end).trim();
                break;
            }
            if (cls.isEmpty()) {
                return fileName.endsWith(".java") ? fileName.substring(0, fileName.length() - 5) : fileName;
            }
            return pkg.isEmpty() ? cls : pkg + "." + cls;
        }
        catch (Exception e) {
            return fileName.endsWith(".java") ? fileName.substring(0, fileName.length() - 5) : fileName;
        }
    }

    private Path resolveFilePath(Path file) {
        Path abs = file.toAbsolutePath().normalize();
        if (Files.exists(abs, new LinkOption[0])) {
            return abs;
        }
        try {
            Path rel = Paths.get(".", new String[0]).toAbsolutePath().normalize().resolve(file).normalize();
            if (Files.exists(rel, new LinkOption[0])) {
                return rel;
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
        return abs;
    }

    public List<Class<?>> getLoadedClasses() {
        return new ArrayList(this.loadedClasses);
    }

    public boolean isAvailable() {
        return this.compiler != null;
    }

    public JavaSourceCompiler getCompiler() {
        return this.compiler;
    }

    public DynamicClassLoader getClassLoader() {
        return this.classLoader;
    }

    public McpToSrgTransformer getMcpTransformer() {
        return this.mcpTransformer;
    }

    public RainJavaLogger getLogger() {
        return this.logger;
    }
}

