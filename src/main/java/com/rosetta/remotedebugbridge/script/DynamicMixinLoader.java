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
package com.rosetta.remotedebugbridge.script;

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
import com.rosetta.remotedebugbridge.RosettaRemoteDebugBridge;
import com.rosetta.remotedebugbridge.script.CompiledClass;
import com.rosetta.remotedebugbridge.script.DynamicClassLoader;
import com.rosetta.remotedebugbridge.script.JavaSourceCompiler;
import com.rosetta.remotedebugbridge.script.helper.BytecodeProviderWrapper;
import com.rosetta.remotedebugbridge.script.helper.MixinServiceHelper;
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
            RosettaRemoteDebugBridge.LOGGER.info("DynamicMixinLoader already initialized");
            return;
        }
        Object object = LOCK;
        synchronized (object) {
            if (initialized) {
                return;
            }
            if (!Files.exists(this.mixinsDir, new LinkOption[0])) {
                RosettaRemoteDebugBridge.LOGGER.info("Mixins directory does not exist: {}", (Object)this.mixinsDir);
                initialized = true;
                return;
            }
            RosettaRemoteDebugBridge.LOGGER.info("========================================");
            RosettaRemoteDebugBridge.LOGGER.info("Loading Dynamic Mixins (Wrapper Method)");
            RosettaRemoteDebugBridge.LOGGER.info("========================================");
            MixinProcessor processor = MixinProcessorHolder.getInstance();
            if (processor == null) {
                RosettaRemoteDebugBridge.LOGGER.warn("MixinProcessor not available: dynamic mixin loading requires the relocated Mixin service (rosetta_remote_debug_bridge-core agent). Skipping.");
                initialized = true;
                return;
            }
            try {
                this.installBytecodeProviderWrapper();
                Map<String, byte[]> bytecodeCache = this.compileAllMixins();
                if (bytecodeCache.isEmpty()) {
                    RosettaRemoteDebugBridge.LOGGER.warn("No mixins were successfully compiled");
                    initialized = true;
                    return;
                }
                for (Map.Entry<String, byte[]> entry : bytecodeCache.entrySet()) {
                    BytecodeProviderWrapper.registerMixin(entry.getKey(), entry.getValue());
                }
                this.registerMixinConfig(processor, bytecodeCache);
            }
            catch (Exception e) {
                RosettaRemoteDebugBridge.LOGGER.error("Failed to load dynamic mixins", (Throwable)e);
            }
            initialized = true;
        }
    }

    private void installBytecodeProviderWrapper() throws Exception {
        RosettaRemoteDebugBridge.LOGGER.info("Installing BytecodeProvider wrapper...");
        Class<?> serviceClass = Class.forName("org.spongepowered.rain.asm.service.MixinService");
        Object service = serviceClass.getMethod("getService", new Class[0]).invoke(null, new Object[0]);
        Object currentProvider = service.getClass().getMethod("getBytecodeProvider", new Class[0]).invoke(service, new Object[0]);
        RosettaRemoteDebugBridge.LOGGER.info("Current BytecodeProvider: {}", (Object)currentProvider.getClass().getName());
        if (currentProvider instanceof BytecodeProviderWrapper) {
            RosettaRemoteDebugBridge.LOGGER.info("BytecodeProvider already wrapped");
            return;
        }
        BytecodeProviderWrapper wrapper = new BytecodeProviderWrapper((IClassBytecodeProvider)currentProvider);
        boolean replaced = false;
        for (Class<?> current = service.getClass(); current != null && current != Object.class; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField("bytecodeProvider");
                field.setAccessible(true);
                field.set(service, wrapper);
                RosettaRemoteDebugBridge.LOGGER.info("\u2705 BytecodeProvider wrapped successfully!");
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
        RosettaRemoteDebugBridge.LOGGER.info("Found {} mixin source files", (Object)mixinFiles.size());
        for (Path file : mixinFiles) {
            try {
                RosettaRemoteDebugBridge.LOGGER.info("Compiling: {}", (Object)file.getFileName());
                CompiledClass compiled = this.compiler.compile(file);
                bytecodeCache.put(compiled.className, compiled.bytecode);
                RosettaRemoteDebugBridge.LOGGER.info("\u2705 Compiled: {}", (Object)compiled.className);
            }
            catch (Exception e) {
                RosettaRemoteDebugBridge.LOGGER.error("Failed to compile: {}", (Object)file, (Object)e);
            }
        }
        return bytecodeCache;
    }

    private void registerMixinConfig(MixinProcessor processor, Map<String, byte[]> bytecodeCache) throws Exception {
        String configName = "dynamic_rosetta_remote_debug_bridge_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        RosettaRemoteDebugBridge.LOGGER.info("Creating config: {}", (Object)configName);
        MixinConfig config = MixinConfig.createDynamic((String)configName, (String)"rosetta.mixins", (int)1000, (boolean)false);
        for (String className : bytecodeCache.keySet()) {
            DefaultMixinConfigPlugin.registerDynamicMixin((String)className);
        }
        Extensions extensions = this.getExtensionsFromProcessor(processor);
        config.setExtensions(extensions);
        MixinServiceHelper.fixConfigService(config);
        try {
            RosettaRemoteDebugBridge.LOGGER.info("\u2705 Plugin initialized for config: {}", (Object)configName);
        }
        catch (Exception e) {
            RosettaRemoteDebugBridge.LOGGER.error("Failed to initialize plugin for config", (Throwable)e);
            throw e;
        }
        int successCount = 0;
        for (Map.Entry<String, byte[]> entry : bytecodeCache.entrySet()) {
            try {
                MixinInfo mixinInfo = config.registerDynamicMixin(entry.getKey(), entry.getValue());
                RosettaRemoteDebugBridge.LOGGER.info("\u2705 Registered: {}", (Object)entry.getKey());
                ++successCount;
            }
            catch (Exception e) {
                RosettaRemoteDebugBridge.LOGGER.error("Failed to register: {}", (Object)entry.getKey(), (Object)e);
            }
        }
        if (successCount > 0) {
            config.prepare(extensions);
            config.postInitialise(extensions);
            this.addConfigToProcessor(processor, config);
            RosettaRemoteDebugBridge.LOGGER.info("========================================");
            RosettaRemoteDebugBridge.LOGGER.info("\u2705 Successfully loaded {} dynamic mixins", (Object)successCount);
            RosettaRemoteDebugBridge.LOGGER.info("========================================");
        } else {
            RosettaRemoteDebugBridge.LOGGER.warn("No mixins were successfully registered");
        }
    }

    private List<Path> scanMixinSources() {
        ArrayList<Path> files = new ArrayList<Path>();
        try (Stream<Path> paths = Files.walk(this.mixinsDir, new FileVisitOption[0]);){
            paths.filter(x$0 -> Files.isRegularFile(x$0, new LinkOption[0])).filter(p -> p.toString().endsWith(".java")).forEach(files::add);
        }
        catch (Exception e) {
            RosettaRemoteDebugBridge.LOGGER.error("Failed to scan mixins directory", (Throwable)e);
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
            RosettaRemoteDebugBridge.LOGGER.error("Failed to get extensions", (Throwable)e);
            return null;
        }
    }

    private void addConfigToProcessor(MixinProcessor processor, MixinConfig config) throws Exception {
        Field configsField = processor.getClass().getDeclaredField("configs");
        configsField.setAccessible(true);
        List<MixinConfig> configs = (List<MixinConfig>)configsField.get(processor);
        for (MixinConfig existing : configs) {
            if (!existing.getName().equals(config.getName())) continue;
            RosettaRemoteDebugBridge.LOGGER.warn("Config already exists: {}", (Object)config.getName());
            return;
        }
        configs.add(config);
        configs.sort(MixinConfig::compareTo);
        RosettaRemoteDebugBridge.LOGGER.info("Added config to processor: {}", (Object)config.getName());
    }
}

