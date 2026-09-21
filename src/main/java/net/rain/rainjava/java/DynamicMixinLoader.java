/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.spongepowered.rain.asm.mixin.transformer.DefaultMixinConfigPlugin
 *  org.spongepowered.rain.asm.mixin.transformer.MixinConfig
 *  org.spongepowered.rain.asm.mixin.transformer.MixinInfo
 *  org.spongepowered.rain.asm.mixin.transformer.MixinProcessor
 *  org.spongepowered.rain.asm.mixin.transformer.MixinProcessorHolder
 *  org.spongepowered.rain.asm.mixin.transformer.ext.Extensions
 *  org.spongepowered.rain.asm.service.IClassBytecodeProvider
 */
package net.rain.rainjava.java;

import java.lang.reflect.Field;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import net.rain.rainjava.RainJava;
import net.rain.rainjava.java.CompiledClass;
import net.rain.rainjava.java.DynamicClassLoader;
import net.rain.rainjava.java.JavaSourceCompiler;
import net.rain.rainjava.java.helper.BytecodeProviderWrapper;
import net.rain.rainjava.java.helper.MixinServiceHelper;
import org.spongepowered.rain.asm.mixin.transformer.DefaultMixinConfigPlugin;
import org.spongepowered.rain.asm.mixin.transformer.MixinConfig;
import org.spongepowered.rain.asm.mixin.transformer.MixinInfo;
import org.spongepowered.rain.asm.mixin.transformer.MixinProcessor;
import org.spongepowered.rain.asm.mixin.transformer.MixinProcessorHolder;
import org.spongepowered.rain.asm.mixin.transformer.ext.Extensions;
import org.spongepowered.rain.asm.service.IClassBytecodeProvider;

public class DynamicMixinLoader {
    private final JavaSourceCompiler compiler;
    private final DynamicClassLoader classLoader;
    private final Path mixinsDir;
    private static volatile boolean initialized = false;
    private static final Object LOCK = new Object();

    public DynamicMixinLoader(JavaSourceCompiler compiler, DynamicClassLoader classLoader, Path rainJavaDir) {
        this.compiler = compiler;
        this.classLoader = classLoader;
        this.mixinsDir = rainJavaDir.resolve("mixins");
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void loadDynamicMixins() {
        if (initialized) {
            RainJava.LOGGER.info("DynamicMixinLoader already initialized");
            return;
        }
        Object object = LOCK;
        synchronized (object) {
            if (initialized) {
                return;
            }
            if (!Files.exists(this.mixinsDir, new LinkOption[0])) {
                RainJava.LOGGER.info("Mixins directory does not exist: {}", (Object)this.mixinsDir);
                initialized = true;
                return;
            }
            RainJava.LOGGER.info("========================================");
            RainJava.LOGGER.info("Loading Dynamic Mixins (Wrapper Method)");
            RainJava.LOGGER.info("========================================");
            MixinProcessor processor = MixinProcessorHolder.getInstance();
            if (processor == null) {
                RainJava.LOGGER.error("MixinProcessor not available");
                return;
            }
            try {
                this.installBytecodeProviderWrapper();
                Map<String, byte[]> bytecodeCache = this.compileAllMixins();
                if (bytecodeCache.isEmpty()) {
                    RainJava.LOGGER.warn("No mixins were successfully compiled");
                    initialized = true;
                    return;
                }
                for (Map.Entry<String, byte[]> entry : bytecodeCache.entrySet()) {
                    BytecodeProviderWrapper.registerMixin(entry.getKey(), entry.getValue());
                }
                this.registerMixinConfig(processor, bytecodeCache);
            }
            catch (Exception e) {
                RainJava.LOGGER.error("Failed to load dynamic mixins", (Throwable)e);
            }
            initialized = true;
        }
    }

    private void installBytecodeProviderWrapper() throws Exception {
        RainJava.LOGGER.info("Installing BytecodeProvider wrapper...");
        Class<?> serviceClass = Class.forName("org.spongepowered.rain.asm.service.MixinService");
        Object service = serviceClass.getMethod("getService", new Class[0]).invoke(null, new Object[0]);
        Object currentProvider = service.getClass().getMethod("getBytecodeProvider", new Class[0]).invoke(service, new Object[0]);
        RainJava.LOGGER.info("Current BytecodeProvider: {}", (Object)currentProvider.getClass().getName());
        if (currentProvider instanceof BytecodeProviderWrapper) {
            RainJava.LOGGER.info("BytecodeProvider already wrapped");
            return;
        }
        BytecodeProviderWrapper wrapper = new BytecodeProviderWrapper((IClassBytecodeProvider)currentProvider);
        boolean replaced = false;
        for (Class<?> current = service.getClass(); current != null && current != Object.class; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField("bytecodeProvider");
                field.setAccessible(true);
                field.set(service, wrapper);
                RainJava.LOGGER.info("\u2705 BytecodeProvider wrapped successfully!");
                replaced = true;
                break;
            }
            catch (NoSuchFieldException e) {
                continue;
            }
        }
        if (!replaced) {
            throw new RuntimeException("Could not find bytecodeProvider field");
        }
    }

    private Map<String, byte[]> compileAllMixins() {
        HashMap<String, byte[]> bytecodeCache = new HashMap<String, byte[]>();
        List<Path> mixinFiles = this.scanMixinSources();
        RainJava.LOGGER.info("Found {} mixin source files", (Object)mixinFiles.size());
        for (Path file : mixinFiles) {
            try {
                RainJava.LOGGER.info("Compiling: {}", (Object)file.getFileName());
                CompiledClass compiled = this.compiler.compile(file);
                bytecodeCache.put(compiled.className, compiled.bytecode);
                RainJava.LOGGER.info("\u2705 Compiled: {}", (Object)compiled.className);
            }
            catch (Exception e) {
                RainJava.LOGGER.error("Failed to compile: {}", (Object)file, (Object)e);
            }
        }
        return bytecodeCache;
    }

    private void registerMixinConfig(MixinProcessor processor, Map<String, byte[]> bytecodeCache) throws Exception {
        String configName = "dynamic_rainjava_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        RainJava.LOGGER.info("Creating config: {}", (Object)configName);
        MixinConfig config = MixinConfig.createDynamic((String)configName, (String)"rainjava.mixins", (int)1000, (boolean)false);
        for (String className : bytecodeCache.keySet()) {
            DefaultMixinConfigPlugin.registerDynamicMixin((String)className);
        }
        Extensions extensions = this.getExtensionsFromProcessor(processor);
        config.setExtensions(extensions);
        MixinServiceHelper.fixConfigService(config);
        try {
            RainJava.LOGGER.info("\u2705 Plugin initialized for config: {}", (Object)configName);
        }
        catch (Exception e) {
            RainJava.LOGGER.error("Failed to initialize plugin for config", (Throwable)e);
            throw e;
        }
        int successCount = 0;
        for (Map.Entry<String, byte[]> entry : bytecodeCache.entrySet()) {
            try {
                MixinInfo mixinInfo = config.registerDynamicMixin(entry.getKey(), entry.getValue());
                RainJava.LOGGER.info("\u2705 Registered: {}", (Object)entry.getKey());
                ++successCount;
            }
            catch (Exception e) {
                RainJava.LOGGER.error("Failed to register: {}", (Object)entry.getKey(), (Object)e);
            }
        }
        if (successCount > 0) {
            config.prepare(extensions);
            config.postInitialise(extensions);
            this.addConfigToProcessor(processor, config);
            RainJava.LOGGER.info("========================================");
            RainJava.LOGGER.info("\u2705 Successfully loaded {} dynamic mixins", (Object)successCount);
            RainJava.LOGGER.info("========================================");
        } else {
            RainJava.LOGGER.warn("No mixins were successfully registered");
        }
    }

    private List<Path> scanMixinSources() {
        ArrayList<Path> files = new ArrayList<Path>();
        try (Stream<Path> paths = Files.walk(this.mixinsDir, new FileVisitOption[0]);){
            paths.filter(x$0 -> Files.isRegularFile(x$0, new LinkOption[0])).filter(p -> p.toString().endsWith(".java")).forEach(files::add);
        }
        catch (Exception e) {
            RainJava.LOGGER.error("Failed to scan mixins directory", (Throwable)e);
        }
        return files;
    }

    private Extensions getExtensionsFromProcessor(MixinProcessor processor) {
        try {
            Field field = processor.getClass().getDeclaredField("extensions");
            field.setAccessible(true);
            return (Extensions)field.get(processor);
        }
        catch (Exception e) {
            RainJava.LOGGER.error("Failed to get extensions", (Throwable)e);
            return null;
        }
    }

    private void addConfigToProcessor(MixinProcessor processor, MixinConfig config) throws Exception {
        Field configsField = processor.getClass().getDeclaredField("configs");
        configsField.setAccessible(true);
        List<MixinConfig> configs = (List<MixinConfig>)configsField.get(processor);
        for (MixinConfig existing : configs) {
            if (!existing.getName().equals(config.getName())) continue;
            RainJava.LOGGER.warn("Config already exists: {}", (Object)config.getName());
            return;
        }
        configs.add(config);
        configs.sort(MixinConfig::compareTo);
        RainJava.LOGGER.info("Added config to processor: {}", (Object)config.getName());
    }
}

