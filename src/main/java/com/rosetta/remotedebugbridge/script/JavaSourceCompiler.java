/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraftforge.fml.loading.FMLPaths
 *  net.rain.repack.ecj.internal.compiler.tool.EclipseCompiler
 *  org.apache.logging.log4j.LogManager
 *  org.apache.logging.log4j.Logger
 */
package com.rosetta.remotedebugbridge.script;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.FileObject;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import net.minecraftforge.fml.loading.FMLPaths;
import com.rosetta.remotedebugbridge.script.CompiledClass;
import net.rain.repack.ecj.internal.compiler.tool.EclipseCompiler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class JavaSourceCompiler {
    private final JavaCompiler compiler;
    private final List<String> options;
    private final boolean isEclipseCompiler;
    private final String classPath;
    public static final Logger LOGGER = LogManager.getLogger();

    public JavaSourceCompiler(JavaCompiler javaCompiler) {
        String classPath;
        if (javaCompiler == null) {
            throw new RuntimeException("No Java compiler available!\nPossible reasons:\n1. You are running on JRE instead of JDK\n2. The java.compiler module is not available\nSolution: Make sure you're using a JDK (not JRE) to run Minecraft/Forge");
        }
        this.compiler = javaCompiler;
        this.isEclipseCompiler = javaCompiler instanceof EclipseCompiler;
        this.options = new ArrayList<String>();
        this.options.add("-source");
        this.options.add("17");
        this.options.add("-target");
        this.options.add("17");
        this.options.add("-encoding");
        this.options.add("UTF-8");
        this.options.add("-warn:none");
        // Mixin annotation processors are only useful for script-side Mixin work and need
        // org.spongepowered.tools.* on the compiler classpath. When they are unavailable
        // (plain server runtime), javac/ECJ aborts with an internal ClassNotFoundException,
        // so annotation processing is opt-in via -Drosetta.mixin.annotationProcessors=true.
        if (Boolean.getBoolean("rosetta.mixin.annotationProcessors")) {
            this.options.add("-processor");
            this.options.add("org.spongepowered.tools.obfuscation.MixinObfuscationProcessorInjection,org.spongepowered.tools.obfuscation.MixinObfuscationProcessorTargets");
            this.options.add("-Amixin.env.remapRefMap=true");
            this.options.add("-Amixin.env.disableTargetExport=true");
            this.options.add("-Amixin.debug.export.decompile=false");
        } else {
            this.options.add("-proc:none");
        }
        this.options.add("-preserveAllLocals");
        this.options.add("-Xdiags:verbose");
        this.options.add("-enableJavadoc");
        this.options.add("-g:vars,lines,source");
        this.options.add("-proceedOnError");
        this.options.add("-XenableNullAnnotations");
        this.options.add("-XJavac");
        this.options.add("-XprintProcessorInfo");
        this.options.add("-verbose");
        this.classPath = classPath = this.buildClassPath();
        this.options.add("-classpath");
        this.options.add(classPath);
        this.options.add("-processorpath");
        this.options.add(classPath);
        if (!this.isEclipseCompiler) {
            this.options.add("-implicit:none");
        }
    }

    private static Path findMinecraftDirectory() {
        Path p;
        String gameDir = System.getProperty("minecraft.gameDir");
        if (gameDir != null && Files.exists(p = Paths.get(gameDir, new String[0]), new LinkOption[0])) {
            if (p.toString().contains("/versions/")) {
                p = p.getParent().getParent();
            }
            return p;
        }
        try {
            String[] args = System.getProperty("sun.java.command", "").split(" ");
            for (int i = 0; i < args.length - 1; ++i) {
                Path p2;
                if (!args[i].equals("--gameDir") || !Files.exists(p2 = Paths.get(args[i + 1], new String[0]), new LinkOption[0])) continue;
                if (p2.toString().contains("/versions/")) {
                    p2 = p2.getParent().getParent();
                }
                return p2;
            }
        }
        catch (Exception args) {
            // empty catch block
        }
        for (String entry : System.getProperty("java.class.path", "").split(File.pathSeparator)) {
            Path p3;
            if (!entry.contains(".minecraft")) continue;
            for (p3 = Paths.get(entry, new String[0]); p3 != null && !p3.getFileName().toString().equals(".minecraft"); p3 = p3.getParent()) {
            }
            if (p3 == null || !Files.exists(p3, new LinkOption[0])) continue;
            return p3;
        }
        String home = System.getProperty("user.home");
        for (String s : new String[]{"/storage/emulated/0/FCL/.minecraft", home + "/.minecraft", home + "/AppData/Roaming/.minecraft", home + "/Library/Application Support/minecraft"}) {
            Path p4 = Paths.get(s, new String[0]);
            if (!Files.exists(p4, new LinkOption[0])) continue;
            return p4;
        }
        return null;
    }

    private String buildClassPath() {
        LinkedHashSet<String> classpathEntries = new LinkedHashSet<String>();
        String systemClassPath = System.getProperty("java.class.path");
        if (systemClassPath != null && !systemClassPath.isEmpty()) {
            classpathEntries.addAll(Arrays.asList(systemClassPath.split(File.pathSeparator)));
        }
        String modulePath = System.getProperty("jdk.module.path");
        if (modulePath != null && !modulePath.isEmpty()) {
            classpathEntries.addAll(Arrays.asList(modulePath.split(File.pathSeparator)));
        }
        try {
            java.security.CodeSource codeSource = JavaSourceCompiler.class.getProtectionDomain().getCodeSource();
            if (codeSource != null && codeSource.getLocation() != null && "file".equals(codeSource.getLocation().getProtocol())) {
                classpathEntries.add(java.nio.file.Paths.get(codeSource.getLocation().toURI()).toString());
            }
        }
        catch (Exception e) {
            LOGGER.debug("[JavaSourceCompiler] Could not resolve own code source: {}", (Object)e.getMessage());
        }
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        this.extractClassPath(classLoader, classpathEntries);
        ClassLoader sysLoader = ClassLoader.getSystemClassLoader();
        if (sysLoader != classLoader) {
            this.extractClassPath(sysLoader, classpathEntries);
        }
        this.scanAllForgeMods(classpathEntries);
        this.findMinecraftJars(classpathEntries);
        this.scanMinecraftRootJars(classpathEntries);
        this.scanModsFolder(classpathEntries);
        this.scanLibrariesFolder(classpathEntries);
        return String.join((CharSequence)File.pathSeparator, classpathEntries);
    }

    private void scanMinecraftRootJars(Set<String> classpathEntries) {
        Path mcRoot = JavaSourceCompiler.findMinecraftDirectory();
        if (mcRoot == null) {
            LOGGER.warn("[JavaSourceCompiler] findMinecraftDirectory() returned null, skipping root jar scan.");
            return;
        }
        LOGGER.info("[JavaSourceCompiler] Scanning Minecraft root for jars: {}", (Object)mcRoot);
        int[] counter = new int[]{0};
        this.scanDirectoryForJars(mcRoot.toFile(), classpathEntries, counter, true);
        LOGGER.info("[JavaSourceCompiler] Added {} jars from Minecraft root.", (Object)counter[0]);
    }

    private void scanDirectoryForJars(File dir, Set<String> classpathEntries, int[] counter, boolean skipSources) {
        if (dir == null || !dir.isDirectory()) {
            return;
        }
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                this.scanDirectoryForJars(file, classpathEntries, counter, skipSources);
                continue;
            }
            if (!file.getName().endsWith(".jar") || skipSources && file.getName().endsWith("-sources.jar") || !classpathEntries.add(file.getAbsolutePath())) continue;
            counter[0] = counter[0] + 1;
            LOGGER.debug("[JavaSourceCompiler] Added jar: {}", (Object)file.getAbsolutePath());
        }
    }

    private void addModuleExports() {
        String[] moduleOpens;
        String[] moduleExports;
        for (String export : moduleExports = new String[]{"org.spongepowered.mixin/org.spongepowered.tools.obfuscation=ALL-UNNAMED", "org.spongepowered.mixin/org.spongepowered.asm.mixin=ALL-UNNAMED", "org.spongepowered.mixin/org.spongepowered.asm.mixin.transformer=ALL-UNNAMED", "org.spongepowered.mixin/org.spongepowered.asm.service=ALL-UNNAMED"}) {
            this.options.add("--add-exports");
            this.options.add(export);
        }
        for (String open : moduleOpens = new String[]{"org.spongepowered.mixin/org.spongepowered.tools.obfuscation=ALL-UNNAMED"}) {
            this.options.add("--add-opens");
            this.options.add(open);
        }
        LOGGER.info("[JavaSourceCompiler] Added module exports for Mixin annotation processor");
    }

    private void scanAllForgeMods(Set<String> classpathEntries) {
        try {
            Class<?> modListClass = Class.forName("net.minecraftforge.fml.ModList");
            Object modList = modListClass.getMethod("get", new Class[0]).invoke(null, new Object[0]);
            Method getModsMethod = modListClass.getMethod("getMods", new Class[0]);
            List mods = (List)getModsMethod.invoke(modList, new Object[0]);
            int addedCount = 0;
            for (Object mod : mods) {
                try {
                    String jarPath;
                    Method getModIdMethod = mod.getClass().getMethod("getModId", new Class[0]);
                    String modId = (String)getModIdMethod.invoke(mod, new Object[0]);
                    Method getModFileMethod = mod.getClass().getMethod("getFile", new Class[0]);
                    Object modFile = getModFileMethod.invoke(mod, new Object[0]);
                    if (modFile == null || (jarPath = this.extractModFilePath(modFile, modId)) == null || !classpathEntries.add(jarPath)) continue;
                    ++addedCount;
                }
                catch (Exception e) {
                    LOGGER.debug("[JavaSourceCompiler]   \u2717 Failed to process mod: " + e.getMessage());
                }
            }
        }
        catch (ClassNotFoundException e) {
            LOGGER.warn("[JavaSourceCompiler] ModList class not found - not running in Forge environment?");
        }
        catch (Exception e) {
            LOGGER.warn("[JavaSourceCompiler] Failed to scan Forge mods: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String extractModFilePath(Object modFile, String modId) {
        try {
            String extracted;
            int startIdx;
            int jarIdx;
            try {
                Method getFilePathMethod = modFile.getClass().getMethod("getFilePath", new Class[0]);
                Object filePath = getFilePathMethod.invoke(modFile, new Object[0]);
                if (filePath != null) {
                    String path = filePath.toString();
                    if (filePath instanceof Path) {
                        path = ((Path)filePath).toAbsolutePath().toString();
                    }
                    return path;
                }
            }
            catch (NoSuchMethodException getFilePathMethod) {
                // empty catch block
            }
            try {
                Method getFileMethod = modFile.getClass().getMethod("getFile", new Class[0]);
                Object file = getFileMethod.invoke(modFile, new Object[0]);
                if (file instanceof File) {
                    return ((File)file).getAbsolutePath();
                }
                if (file != null) {
                    return file.toString();
                }
            }
            catch (NoSuchMethodException getFileMethod) {
                // empty catch block
            }
            try {
                Method getPathMethod;
                Object path;
                Method getSecureJarMethod = modFile.getClass().getMethod("getSecureJar", new Class[0]);
                Object secureJar = getSecureJarMethod.invoke(modFile, new Object[0]);
                if (secureJar != null && (path = (getPathMethod = secureJar.getClass().getMethod("getPrimaryPath", new Class[0])).invoke(secureJar, new Object[0])) instanceof Path) {
                    return ((Path)path).toAbsolutePath().toString();
                }
            }
            catch (NoSuchMethodException getSecureJarMethod) {
                // empty catch block
            }
            String str = modFile.toString();
            if (str.contains(".jar") && (jarIdx = str.indexOf(".jar")) > 0 && (startIdx = str.lastIndexOf("file:", jarIdx)) >= 0 && new File(extracted = str.substring(startIdx + 5, jarIdx + 4)).exists()) {
                return extracted;
            }
        }
        catch (Exception e) {
            LOGGER.debug("[JavaSourceCompiler] Could not extract path for mod " + modId + ": " + e.getMessage());
        }
        return null;
    }

    private void scanModsFolder(Set<String> classpathEntries) {
        try {
            Path modsdir;
            String gameDir;
            String workDir;
            ArrayList<File> possibleModsFolders = new ArrayList<File>();
            Path mcRoot = JavaSourceCompiler.findMinecraftDirectory();
            if (mcRoot != null) {
                possibleModsFolders.add(mcRoot.resolve("mods").toFile());
            }
            if ((workDir = System.getProperty("user.dir")) != null) {
                possibleModsFolders.add(new File(workDir, "mods"));
            }
            if ((gameDir = System.getProperty("minecraft.gameDir")) != null) {
                possibleModsFolders.add(new File(gameDir, "mods"));
            }
            if ((modsdir = FMLPaths.GAMEDIR.get().resolve("mods")) != null) {
                possibleModsFolders.add(modsdir.toFile());
            }
            int addedCount = 0;
            for (File modsFolder : possibleModsFolders) {
                File[] modFiles;
                if (!modsFolder.exists() || !modsFolder.isDirectory() || (modFiles = modsFolder.listFiles((dir, name) -> name.endsWith(".jar") && !name.endsWith("-sources.jar"))) == null) continue;
                for (File modFile : modFiles) {
                    if (!classpathEntries.add(modFile.getAbsolutePath())) continue;
                    ++addedCount;
                }
            }
            LOGGER.info("[JavaSourceCompiler] Added {} jars from mods folders.", (Object)addedCount);
        }
        catch (Exception e) {
            LOGGER.warn("[JavaSourceCompiler] Failed to scan mods folder: " + e.getMessage());
        }
    }

    private void scanLibrariesFolder(Set<String> classpathEntries) {
        try {
            String gameDir;
            File libDir;
            Path mcRoot = JavaSourceCompiler.findMinecraftDirectory();
            File file = libDir = mcRoot != null ? mcRoot.resolve("libraries").toFile() : null;
            if (!(libDir != null && libDir.exists() || (gameDir = System.getProperty("minecraft.gameDir")) == null)) {
                libDir = new File(gameDir, "libraries");
            }
            if (libDir == null || !libDir.exists()) {
                libDir = new File("/storage/emulated/0/FCL/.minecraft/libraries");
            }
            if (libDir != null && libDir.exists() && libDir.isDirectory()) {
                int[] addedCount = new int[]{0};
                this.scanDirectoryForJars(libDir, classpathEntries, addedCount, true);
                LOGGER.info("[JavaSourceCompiler] Added {} jars from libraries folder.", (Object)addedCount[0]);
            } else {
                LOGGER.warn("[JavaSourceCompiler] Libraries folder not found.");
            }
        }
        catch (Exception e) {
            LOGGER.warn("[JavaSourceCompiler] Failed to scan libraries: " + e.getMessage());
        }
    }

    private void findMinecraftJars(Set<String> classpathEntries) {
        String[] knownClasses = new String[]{"net.minecraft.world.item.Item", "net.minecraft.world.level.block.Block", "net.minecraftforge.registries.ForgeRegistries", "net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext", "net.minecraftforge.registries.DeferredRegister", "net.minecraftforge.registries.RegistryObject"};
        int foundCount = 0;
        for (String className : knownClasses) {
            try {
                String path;
                String resPath;
                URL resUrl;
                String resourceName;
                URL classUrl;
                String path2;
                Class<?> clazz = Class.forName(className);
                try {
                    URL location;
                    ProtectionDomain pd = clazz.getProtectionDomain();
                    if (pd != null && pd.getCodeSource() != null && (location = pd.getCodeSource().getLocation()) != null && (path2 = this.resolveJarPath(location)) != null && classpathEntries.add(path2)) {
                        ++foundCount;
                        continue;
                    }
                }
                catch (Exception pd) {
                    // empty catch block
                }
                if ((classUrl = clazz.getResource(resourceName = "/" + className.replace('.', '/') + ".class")) != null && (path2 = this.resolveJarPath(classUrl)) != null && classpathEntries.add(path2)) {
                    ++foundCount;
                    continue;
                }
                ClassLoader cl = clazz.getClassLoader();
                if (cl == null || (resUrl = cl.getResource(resPath = className.replace('.', '/') + ".class")) == null || (path = this.resolveJarPath(resUrl)) == null || !classpathEntries.add(path)) continue;
                ++foundCount;
            }
            catch (ClassNotFoundException classNotFoundException) {
            }
            catch (Exception exception) {
                // empty catch block
            }
        }
        LOGGER.info("[JavaSourceCompiler] Found {} Minecraft/Forge core jars.", (Object)foundCount);
    }

    private String resolveJarPath(URL url) {
        if (url == null) {
            return null;
        }
        try {
            String filePath;
            File file;
            int endIdx;
            String urlStr = url.toString();
            if (urlStr.startsWith("jar:file:") && (endIdx = urlStr.indexOf("!")) > 0 && (file = new File(filePath = URLDecoder.decode(urlStr.substring(9, endIdx), "UTF-8"))).exists()) {
                return file.getAbsolutePath();
            }
            if (urlStr.startsWith("union:")) {
                File file2;
                String remaining = urlStr.substring(6).replaceAll("%23\\d+!", "").replaceAll("!.*$", "");
                if (remaining.startsWith("//")) {
                    remaining = remaining.substring(2);
                }
                if ((file2 = new File(remaining = URLDecoder.decode(remaining, "UTF-8"))).exists()) {
                    return file2.getAbsolutePath();
                }
            }
            if (urlStr.startsWith("file:")) {
                int exclamIdx;
                String filePath2 = urlStr.substring(5);
                if (filePath2.startsWith("//")) {
                    filePath2 = filePath2.substring(2);
                }
                if ((exclamIdx = (filePath2 = URLDecoder.decode(filePath2, "UTF-8")).indexOf("!")) > 0) {
                    filePath2 = filePath2.substring(0, exclamIdx);
                }
                if ((file = new File(filePath2)).exists()) {
                    return file.getAbsolutePath();
                }
            }
        }
        catch (Exception e) {
            LOGGER.debug("[JavaSourceCompiler] Could not resolve jar path from: " + String.valueOf(url) + " - " + e.getMessage());
        }
        return null;
    }

    public String getClassPath() {
        return this.classPath;
    }

    private static String readBinaryClassName(byte[] classBytes) {
        if (classBytes == null || classBytes.length < 12) {
            return null;
        }
        try {
            int constantPoolCount = (classBytes[8] & 0xFF) << 8 | classBytes[9] & 0xFF;
            int[] utf8Offset = new int[constantPoolCount];
            int[] utf8Length = new int[constantPoolCount];
            int[] classNameIndex = new int[constantPoolCount];
            int p = 10;
            for (int i = 1; i < constantPoolCount; ++i) {
                int tag = classBytes[p++] & 0xFF;
                switch (tag) {
                    case 1: {
                        int length = (classBytes[p] & 0xFF) << 8 | classBytes[p + 1] & 0xFF;
                        utf8Offset[i] = p + 2;
                        utf8Length[i] = length;
                        p += 2 + length;
                        break;
                    }
                    case 7: {
                        classNameIndex[i] = (classBytes[p] & 0xFF) << 8 | classBytes[p + 1] & 0xFF;
                        p += 2;
                        break;
                    }
                    case 8:
                    case 16:
                    case 19:
                    case 20: {
                        p += 2;
                        break;
                    }
                    case 15: {
                        p += 3;
                        break;
                    }
                    case 3:
                    case 4:
                    case 9:
                    case 10:
                    case 11:
                    case 12:
                    case 17:
                    case 18: {
                        p += 4;
                        break;
                    }
                    case 5:
                    case 6: {
                        p += 8;
                        ++i;
                        break;
                    }
                    default: {
                        return null;
                    }
                }
            }
            int thisClassIndex = (classBytes[p + 2] & 0xFF) << 8 | classBytes[p + 3] & 0xFF;
            int nameIndex = classNameIndex[thisClassIndex];
            if (nameIndex <= 0 || nameIndex >= constantPoolCount || utf8Offset[nameIndex] <= 0) {
                return null;
            }
            String internalName = new String(classBytes, utf8Offset[nameIndex], utf8Length[nameIndex], java.nio.charset.StandardCharsets.UTF_8);
            return internalName.replace('/', '.');
        }
        catch (Throwable t) {
            return null;
        }
    }

    private void extractClassPath(ClassLoader classLoader, Set<String> classpathEntries) {
        if (classLoader == null) {
            return;
        }
        try {
            if (classLoader instanceof URLClassLoader) {
                URLClassLoader urlClassLoader = (URLClassLoader)classLoader;
                for (URL url : urlClassLoader.getURLs()) {
                    try {
                        classpathEntries.add(new File(url.toURI()).getAbsolutePath());
                    }
                    catch (Exception e) {
                        String urlStr = url.toString();
                        if (!urlStr.startsWith("file:")) continue;
                        classpathEntries.add(urlStr.substring(5));
                    }
                }
            }
            try {
                Class<?> builtinLoaderClass = Class.forName("jdk.internal.loader.BuiltinClassLoader");
                if (builtinLoaderClass.isInstance(classLoader)) {
                    URL[] urls;
                    Field ucpField = builtinLoaderClass.getDeclaredField("ucp");
                    ucpField.setAccessible(true);
                    Object ucp = ucpField.get(classLoader);
                    Method getURLsMethod = ucp.getClass().getMethod("getURLs", new Class[0]);
                    for (URL url : urls = (URL[])getURLsMethod.invoke(ucp, new Object[0])) {
                        try {
                            classpathEntries.add(new File(url.toURI()).getAbsolutePath());
                        }
                        catch (Exception exception) {
                            // empty catch block
                        }
                    }
                }
            }
            catch (Exception exception) {
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
        this.extractClassPath(classLoader.getParent(), classpathEntries);
    }

    public CompiledClass compile(Path sourceFile) throws Exception {
        if (!Files.exists(sourceFile, new LinkOption[0])) {
            throw new IllegalArgumentException("Source file does not exist: " + String.valueOf(sourceFile.toAbsolutePath()));
        }
        String source = Files.readString(sourceFile);
        String className = this.extractClassName(source);
        if (className == null || className.isEmpty()) {
            throw new IllegalArgumentException("Could not extract class name from source file: " + String.valueOf(sourceFile));
        }
        return this.compileFromString(className, source, sourceFile.toString());
    }

    public CompiledClass compileFromString(String className, String source) throws Exception {
        LOGGER.info("Compiling from string: {}", (Object)className);
        LOGGER.debug("Source code length: {} characters", (Object)source.length());
        CompiledClass result = this.compileFromString(className, source, className + ".java");
        if (result == null) {
            LOGGER.error("Compilation returned null for class: {}", (Object)className);
            return null;
        }
        if (result.bytecode == null || result.bytecode.length == 0) {
            LOGGER.error("Compilation produced no bytecode for class: {}", (Object)className);
            return null;
        }
        LOGGER.info("Successfully compiled: {} ({} bytes)", (Object)className, (Object)result.bytecode.length);
        return result;
    }

    public CompiledClass compileFromString(String className, String source, String sourceName) throws Exception {
        CustomFileManager fileManager = new CustomFileManager(this.isEclipseCompiler);
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
        SimpleJavaFileObject sourceFileObject = this.isEclipseCompiler ? new EclipseCompatibleJavaFileObject(className, source) : new InMemoryJavaFileObject(className, source);
        JavaCompiler.CompilationTask task = this.compiler.getTask(null, fileManager, diagnostics, this.options, null, Collections.singletonList(sourceFileObject));
        boolean success = task.call();
        if (!success) {
            StringBuilder errors = new StringBuilder();
            errors.append("Compilation failed for: ").append(sourceName).append("\n");
            errors.append("Class name: ").append(className).append("\n");
            errors.append("Compiler: ").append(this.isEclipseCompiler ? "Eclipse JDT" : "System Java Compiler").append("\n");
            errors.append("Errors:\n");
            for (Diagnostic diagnostic : diagnostics.getDiagnostics()) {
                errors.append("  Line ").append(diagnostic.getLineNumber()).append(": ").append(diagnostic.getMessage(null)).append("\n");
            }
            throw new RuntimeException(errors.toString());
        }
        byte[] bytecode = fileManager.getCompiledClass(className);
        if (bytecode == null) {
            LOGGER.error("[JavaSourceCompiler] ERROR: No bytecode generated!");
            LOGGER.error("[JavaSourceCompiler] Requested class: " + className);
            LOGGER.error("[JavaSourceCompiler] Available outputs: " + String.valueOf(fileManager.outputFiles.keySet()));
            throw new RuntimeException("No bytecode generated for class: " + className + ". Available: " + String.valueOf(fileManager.outputFiles.keySet()));
        }
        return new CompiledClass(className, bytecode, fileManager.getAllCompiledClasses());
    }

    private String extractClassName(String source) {
        try {
            String[] keywords;
            int semicolon;
            String packageName = "";
            String className = "";
            String cleanSource = source.replaceAll("//.*", "").replaceAll("/\\*.*?\\*/", "");
            int packageIndex = cleanSource.indexOf("package ");
            if (packageIndex >= 0 && (semicolon = cleanSource.indexOf(";", packageIndex)) > packageIndex) {
                packageName = cleanSource.substring(packageIndex + 8, semicolon).trim();
            }
            for (String keyword : keywords = new String[]{"class ", "interface ", "enum ", "record "}) {
                String after;
                String[] tokens;
                int idx = cleanSource.indexOf(keyword);
                if (idx < 0 || (tokens = (after = cleanSource.substring(idx + keyword.length())).split("[\\s{<]")).length < 1 || tokens[0].trim().isEmpty()) continue;
                className = tokens[0].trim();
                break;
            }
            if (className.isEmpty()) {
                return null;
            }
            return packageName.isEmpty() ? className : packageName + "." + className;
        }
        catch (Exception e) {
            return null;
        }
    }

    private static class EclipseCompatibleJavaFileObject
    extends SimpleJavaFileObject {
        private final String code;
        private final String className;

        public EclipseCompatibleJavaFileObject(String className, String code) {
            super(EclipseCompatibleJavaFileObject.createVirtualURI(className), JavaFileObject.Kind.SOURCE);
            this.code = code;
            this.className = className;
        }

        private static URI createVirtualURI(String className) {
            String path = "/virtual/" + className.replace('.', '/') + ".java";
            return URI.create("file://" + path);
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return this.code;
        }

        @Override
        public InputStream openInputStream() throws IOException {
            return new ByteArrayInputStream(this.code.getBytes("UTF-8"));
        }

        @Override
        public Reader openReader(boolean ignoreEncodingErrors) throws IOException {
            return new StringReader(this.code);
        }

        @Override
        public String getName() {
            return this.className.replace('.', '/') + ".java";
        }
    }

    private static class InMemoryJavaFileObject
    extends SimpleJavaFileObject {
        private final String code;
        private ByteArrayOutputStream bytecode;

        public InMemoryJavaFileObject(String className, String code) {
            super(URI.create("string:///" + className.replace('.', '/') + JavaFileObject.Kind.SOURCE.extension), JavaFileObject.Kind.SOURCE);
            this.code = code;
        }

        public InMemoryJavaFileObject(String className, JavaFileObject.Kind kind) {
            super(URI.create("string:///" + className.replace('.', '/') + kind.extension), kind);
            this.code = null;
            this.bytecode = new ByteArrayOutputStream();
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return this.code;
        }

        @Override
        public OutputStream openOutputStream() {
            return this.bytecode;
        }

        public byte[] getBytes() {
            return this.bytecode != null ? this.bytecode.toByteArray() : null;
        }
    }

    private class CustomFileManager
    implements JavaFileManager {
        private final Map<String, OutputJavaFileObject> outputFiles = new HashMap<String, OutputJavaFileObject>();
        private final StandardJavaFileManager standardManager;
        private final boolean isEclipse;

        public CustomFileManager(boolean isEclipseCompiler) {
            this.isEclipse = isEclipseCompiler;
            JavaCompiler systemCompiler = ToolProvider.getSystemJavaCompiler();
            if (systemCompiler == null) {
                systemCompiler = new EclipseCompiler();
            }
            this.standardManager = systemCompiler.getStandardFileManager(null, null, null);
        }

        public Map<String, byte[]> getAllCompiledClasses() {
            HashMap<String, byte[]> classes = new HashMap<String, byte[]>();
            for (OutputJavaFileObject file : this.outputFiles.values()) {
                byte[] bytes = file.getBytes();
                if (bytes == null || bytes.length <= 0) continue;
                String binaryName = JavaSourceCompiler.readBinaryClassName(bytes);
                if (binaryName == null || binaryName.isEmpty()) continue;
                classes.put(binaryName, bytes);
            }
            return classes;
        }

        public byte[] getCompiledClass(String className) {
            OutputJavaFileObject direct = this.outputFiles.get(className);
            if (direct != null && direct.getBytes() != null && direct.getBytes().length > 0) {
                return direct.getBytes();
            }
            for (OutputJavaFileObject file : this.outputFiles.values()) {
                byte[] bytes = file.getBytes();
                if (bytes == null || bytes.length <= 0) continue;
                if (className.equals(JavaSourceCompiler.readBinaryClassName(bytes))) {
                    return bytes;
                }
            }
            for (OutputJavaFileObject file : this.outputFiles.values()) {
                byte[] bytes = file.getBytes();
                if (bytes == null || bytes.length <= 0) continue;
                String binaryName = JavaSourceCompiler.readBinaryClassName(bytes);
                if (binaryName != null && binaryName.startsWith(className + "$")) {
                    return bytes;
                }
            }
            for (OutputJavaFileObject output : this.outputFiles.values()) {
                byte[] bytes = output.getBytes();
                if (bytes == null || bytes.length <= 0) continue;
                return bytes;
            }
            return null;
        }

        @Override
        public JavaFileObject getJavaFileForOutput(JavaFileManager.Location location, String className, JavaFileObject.Kind kind, FileObject sibling) {
            if (kind == JavaFileObject.Kind.CLASS) {
                OutputJavaFileObject file = new OutputJavaFileObject(className, kind);
                this.outputFiles.put(className, file);
                return file;
            }
            try {
                return this.standardManager.getJavaFileForOutput(location, className, kind, sibling);
            }
            catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public JavaFileObject getJavaFileForInput(JavaFileManager.Location location, String className, JavaFileObject.Kind kind) {
            try {
                return this.standardManager.getJavaFileForInput(location, className, kind);
            }
            catch (IOException e) {
                return null;
            }
        }

        @Override
        public ClassLoader getClassLoader(JavaFileManager.Location location) {
            return this.standardManager.getClassLoader(location);
        }

        @Override
        public Iterable<JavaFileObject> list(JavaFileManager.Location location, String packageName, Set<JavaFileObject.Kind> kinds, boolean recurse) throws IOException {
            if (location == StandardLocation.SOURCE_PATH && kinds.contains((Object)JavaFileObject.Kind.SOURCE)) {
                return Collections.emptyList();
            }
            return this.standardManager.list(location, packageName, kinds, recurse);
        }

        @Override
        public String inferBinaryName(JavaFileManager.Location location, JavaFileObject file) {
            if (file instanceof InMemoryJavaFileObject || file instanceof OutputJavaFileObject || file instanceof EclipseCompatibleJavaFileObject) {
                String uri = file.toUri().toString();
                int startIdx = uri.indexOf("///");
                if (startIdx == -1) {
                    startIdx = uri.lastIndexOf("/");
                }
                if (startIdx != -1) {
                    String path = uri.substring(startIdx + (uri.indexOf("///") != -1 ? 3 : 1));
                    if (path.endsWith(".java")) {
                        path = path.substring(0, path.length() - 5);
                    } else if (path.endsWith(".class")) {
                        path = path.substring(0, path.length() - 6);
                    }
                    return path.replace('/', '.');
                }
            }
            return this.standardManager.inferBinaryName(location, file);
        }

        @Override
        public boolean isSameFile(FileObject a, FileObject b) {
            return this.standardManager.isSameFile(a, b);
        }

        @Override
        public boolean handleOption(String current, Iterator<String> remaining) {
            return this.standardManager.handleOption(current, remaining);
        }

        @Override
        public boolean hasLocation(JavaFileManager.Location location) {
            if (location == StandardLocation.SOURCE_PATH) {
                return true;
            }
            return this.standardManager.hasLocation(location);
        }

        @Override
        public boolean contains(JavaFileManager.Location location, FileObject file) {
            if (location == StandardLocation.SOURCE_PATH && file instanceof JavaFileObject && ((JavaFileObject)file).getKind() == JavaFileObject.Kind.SOURCE) {
                return true;
            }
            try {
                return JavaFileManager.super.contains(location, file);
            }
            catch (IOException e) {
                return false;
            }
        }

        @Override
        public FileObject getFileForInput(JavaFileManager.Location location, String packageName, String relativeName) throws IOException {
            return this.standardManager.getFileForInput(location, packageName, relativeName);
        }

        @Override
        public FileObject getFileForOutput(JavaFileManager.Location location, String packageName, String relativeName, FileObject sibling) throws IOException {
            return this.standardManager.getFileForOutput(location, packageName, relativeName, sibling);
        }

        @Override
        public void flush() throws IOException {
            this.standardManager.flush();
        }

        @Override
        public void close() throws IOException {
            this.standardManager.close();
        }

        @Override
        public int isSupportedOption(String option) {
            return this.standardManager.isSupportedOption(option);
        }
    }

    private static class OutputJavaFileObject
    extends SimpleJavaFileObject {
        private ByteArrayOutputStream bytecode;
        private final String className;

        public OutputJavaFileObject(String className, JavaFileObject.Kind kind) {
            super(URI.create("bytes:///" + className.replace('.', '/') + kind.extension), kind);
            this.className = className;
            this.bytecode = new ByteArrayOutputStream();
        }

        @Override
        public OutputStream openOutputStream() throws IOException {
            this.bytecode = new ByteArrayOutputStream();
            return this.bytecode;
        }

        @Override
        public Writer openWriter() throws IOException {
            throw new UnsupportedOperationException("Writing to class file as text is not supported");
        }

        public byte[] getBytes() {
            return this.bytecode != null ? this.bytecode.toByteArray() : null;
        }
    }
}

