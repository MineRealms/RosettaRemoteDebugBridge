/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.apache.logging.log4j.LogManager
 *  org.apache.logging.log4j.Logger
 */
package net.rain.rainjava.java.helper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import sun.misc.Unsafe;

public class RuntimeModuleOpener {
    private static final Logger LOGGER = LogManager.getLogger();
    private static boolean initialized = false;
    private static boolean javaBaseOpened = false;
    private static final Object LOCK = new Object();
    private static final String RAIN_MIXIN_PACKAGE = "org.spongepowered.asm.mixin";
    private static final String RAIN_TOOLS_PACKAGE = "org.spongepowered.tools.obfuscation";

    private static void ensureBidirectionalAccess(Object javaBaseModule, Object targetModule, String pkg, Class<?> moduleClass) {
        try {
            Method implAddOpens = moduleClass.getDeclaredMethod("implAddOpens", String.class, moduleClass);
            implAddOpens.setAccessible(true);
            implAddOpens.invoke(javaBaseModule, pkg, targetModule);
            return;
        }
        catch (Exception e) {
            block8: {
                try {
                    LOGGER.debug("implAddOpens failed: {}", (Object)e.getMessage());
                    try {
                        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
                        unsafeField.setAccessible(true);
                        Unsafe unsafe = (Unsafe)unsafeField.get(null);
                        Field openPackagesField = moduleClass.getDeclaredField("openPackages");
                        long offset = unsafe.objectFieldOffset(openPackagesField);
                        Object openPackages = unsafe.getObject(javaBaseModule, offset);
                        if (!(openPackages instanceof Map)) break block8;
                        Map map = (Map)openPackages;
                        Set targets = (Set)map.computeIfAbsent(pkg, k -> new HashSet());
                        targets.add(targetModule);
                        try {
                            Field everyoneField = moduleClass.getDeclaredField("EVERYONE_MODULE");
                            everyoneField.setAccessible(true);
                            Object everyone = everyoneField.get(null);
                            targets.add(everyone);
                        }
                        catch (NoSuchFieldException noSuchFieldException) {
                        }
                    }
                    catch (Exception e2) {
                        LOGGER.debug("Unsafe bidirectional access failed: {}", (Object)e2.getMessage());
                    }
                }
                catch (Exception e3) {
                    LOGGER.debug("ensureBidirectionalAccess failed: {}", (Object)e3.getMessage());
                }
            }
            return;
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public static void openJavaBaseModule() {
        if (javaBaseOpened) {
            return;
        }
        Object object = LOCK;
        synchronized (object) {
            if (javaBaseOpened) {
                return;
            }
            try {
                Object currentModule = RuntimeModuleOpener.getModuleOf(RuntimeModuleOpener.class);
                Object mixinModule = RuntimeModuleOpener.getMixinModule();
                Object javaBaseModule = RuntimeModuleOpener.getJavaBaseModule();
                if (javaBaseModule == null) {
                    LOGGER.error("[RuntimeModuleOpener] Failed to get java.base module!");
                    return;
                }
                Class<?> moduleClass = javaBaseModule.getClass();
                String[] criticalPackages = new String[]{"java.lang", "java.lang.reflect", "java.util", "jdk.internal.reflect"};
                boolean i = false;
                for (String pkg : criticalPackages) {
                    RuntimeModuleOpener.openPackageToAllUnnamed(javaBaseModule, pkg, moduleClass);
                    RuntimeModuleOpener.exportPackageToAllUnnamed(javaBaseModule, pkg, moduleClass);
                    if (currentModule != null) {
                        RuntimeModuleOpener.openPackageToModule(javaBaseModule, pkg, currentModule, moduleClass);
                        RuntimeModuleOpener.exportPackageToModule(javaBaseModule, pkg, currentModule, moduleClass);
                        RuntimeModuleOpener.ensureBidirectionalAccess(javaBaseModule, currentModule, pkg, moduleClass);
                    }
                    if (mixinModule == null) continue;
                    RuntimeModuleOpener.openPackageToModule(javaBaseModule, pkg, mixinModule, moduleClass);
                    RuntimeModuleOpener.exportPackageToModule(javaBaseModule, pkg, mixinModule, moduleClass);
                    RuntimeModuleOpener.ensureBidirectionalAccess(javaBaseModule, mixinModule, pkg, moduleClass);
                }
                RuntimeModuleOpener.tryUnsafeOpenJavaBase(javaBaseModule, criticalPackages);
                javaBaseOpened = true;
            }
            catch (Exception e) {
                LOGGER.error("[RuntimeModuleOpener] Failed to open java.base module", (Throwable)e);
            }
        }
    }

    private static Object getMixinModule() {
        try {
            Class<?> mixinClass = Class.forName("org.spongepowered.asm.mixin.Mixin");
            Object module = RuntimeModuleOpener.getModuleOf(mixinClass);
            if (module != null) {
                String moduleName = RuntimeModuleOpener.getModuleName(module);
                return module;
            }
        }
        catch (ClassNotFoundException e) {
            LOGGER.warn("[RuntimeModuleOpener] Mixin class not found");
        }
        return null;
    }

    private static Object getJavaBaseModule() {
        try {
            String name;
            Object module = RuntimeModuleOpener.getModuleOf(Object.class);
            if (module != null && "java.base".equals(name = RuntimeModuleOpener.getModuleName(module))) {
                return module;
            }
            try {
                Class<?> moduleLayerClass = Class.forName("java.lang.ModuleLayer");
                Object bootLayer = moduleLayerClass.getMethod("boot", new Class[0]).invoke(null, new Object[0]);
                Set modules = (Set)bootLayer.getClass().getMethod("modules", new Class[0]).invoke(bootLayer, new Object[0]);
                for (Object mod : modules) {
                    String name2 = RuntimeModuleOpener.getModuleName(mod);
                    if (!"java.base".equals(name2)) continue;
                    return mod;
                }
            }
            catch (Exception e) {
                LOGGER.debug("[RuntimeModuleOpener] ModuleLayer lookup failed: {}", (Object)e.getMessage());
            }
        }
        catch (Exception e) {
            LOGGER.error("[RuntimeModuleOpener] Failed to get java.base module: {}", (Object)e.getMessage());
        }
        return null;
    }

    private static void openPackageToAllUnnamed(Object sourceModule, String pkg, Class<?> moduleClass) {
        try {
            Method getPackagesMethod = moduleClass.getMethod("getPackages", new Class[0]);
            Set packages = (Set)getPackagesMethod.invoke(sourceModule, new Object[0]);
            if (!packages.contains(pkg)) {
                LOGGER.trace("[RuntimeModuleOpener] Package {} not in module, skipping", (Object)pkg);
                return;
            }
            try {
                Method implAddOpensToAllUnnamed = moduleClass.getDeclaredMethod("implAddOpensToAllUnnamed", String.class);
                implAddOpensToAllUnnamed.setAccessible(true);
                implAddOpensToAllUnnamed.invoke(sourceModule, pkg);
                return;
            }
            catch (NoSuchMethodException e) {
                LOGGER.debug("implAddOpensToAllUnnamed not available");
                try {
                    Method addOpensMethod = moduleClass.getMethod("addOpens", String.class, moduleClass);
                    Object unnamedModule = RuntimeModuleOpener.getUnnamedModule();
                    if (unnamedModule != null) {
                        addOpensMethod.invoke(sourceModule, pkg, unnamedModule);
                    }
                }
                catch (Exception e2) {
                    LOGGER.debug("addOpens to unnamed failed: {}", (Object)e2.getMessage());
                }
            }
        }
        catch (Exception e) {
            LOGGER.debug("[RuntimeModuleOpener] Could not open package {}: {}", (Object)pkg, (Object)e.getMessage());
        }
    }

    private static void exportPackageToAllUnnamed(Object sourceModule, String pkg, Class<?> moduleClass) {
        try {
            Method getPackagesMethod = moduleClass.getMethod("getPackages", new Class[0]);
            Set packages = (Set)getPackagesMethod.invoke(sourceModule, new Object[0]);
            if (!packages.contains(pkg)) {
                return;
            }
            try {
                Method implAddExportsToAllUnnamed = moduleClass.getDeclaredMethod("implAddExportsToAllUnnamed", String.class);
                implAddExportsToAllUnnamed.setAccessible(true);
                implAddExportsToAllUnnamed.invoke(sourceModule, pkg);
                return;
            }
            catch (NoSuchMethodException e) {
                LOGGER.debug("implAddExportsToAllUnnamed not available");
            }
        }
        catch (Exception e) {
            LOGGER.debug("[RuntimeModuleOpener] Could not export package {}: {}", (Object)pkg, (Object)e.getMessage());
        }
    }

    private static Object getUnnamedModule() {
        try {
            ClassLoader cl = ClassLoader.getSystemClassLoader();
            Method getUnnamedModule = ClassLoader.class.getMethod("getUnnamedModule", new Class[0]);
            return getUnnamedModule.invoke(cl, new Object[0]);
        }
        catch (Exception e) {
            return null;
        }
    }

    private static void tryUnsafeOpenJavaBase(Object module, String[] packages) {
        try {
            Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            Unsafe unsafe = (Unsafe)unsafeField.get(null);
            Class<?> moduleClass = module.getClass();
            try {
                Field openPackagesField = moduleClass.getDeclaredField("openPackages");
                long offset = unsafe.objectFieldOffset(openPackagesField);
                Object openPackages = unsafe.getObject(module, offset);
                if (openPackages instanceof Map) {
                    Map map = (Map)openPackages;
                    for (String pkg : packages) {
                        HashSet<Object> targets = (HashSet<Object>)map.get(pkg);
                        if (targets == null) {
                            targets = new HashSet<Object>();
                            map.put(pkg, targets);
                        }
                        try {
                            Field everyoneField = moduleClass.getDeclaredField("EVERYONE_MODULE");
                            everyoneField.setAccessible(true);
                            Object everyone = everyoneField.get(null);
                            targets.add(everyone);
                        }
                        catch (NoSuchFieldException noSuchFieldException) {
                            // empty catch block
                        }
                    }
                }
            }
            catch (NoSuchFieldException e) {
                LOGGER.debug("openPackages field not found");
            }
        }
        catch (Exception e) {
            LOGGER.debug("Unsafe method for java.base failed: {}", (Object)e.getMessage());
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public static void openMixinModules() {
        if (initialized) {
            return;
        }
        Object object = LOCK;
        synchronized (object) {
            if (initialized) {
                return;
            }
            try {
                Class<?> mixinClass;
                RuntimeModuleOpener.openJavaBaseModule();
                try {
                    mixinClass = Class.forName("org.spongepowered.asm.mixin.Mixin");
                }
                catch (ClassNotFoundException e) {
                    LOGGER.warn("[RuntimeModuleOpener] Rain Mixin not found, skipping module opening");
                    initialized = true;
                    return;
                }
                Object mixinModule = RuntimeModuleOpener.getModuleOf(mixinClass);
                if (mixinModule == null) {
                    initialized = true;
                    return;
                }
                Object currentModule = RuntimeModuleOpener.getModuleOf(RuntimeModuleOpener.class);
                Object ecjModule = RuntimeModuleOpener.getECJModule();
                String[] criticalPackages = new String[]{RAIN_MIXIN_PACKAGE, "org.spongepowered.asm.mixin.transformer", "org.spongepowered.asm.mixin.transformer.ext", "org.spongepowered.asm.mixin.injection", "org.spongepowered.asm.mixin.injection.struct", "org.spongepowered.asm.mixin.injection.callback", "org.spongepowered.asm.mixin.injection.invoke", "org.spongepowered.asm.mixin.injection.throwables", "org.spongepowered.asm.mixin.gen", "org.spongepowered.asm.service", "org.spongepowered.asm.util", "org.spongepowered.asm.lib", "org.spongepowered.asm.mixin.transformer", RAIN_TOOLS_PACKAGE, "org.spongepowered.tools.obfuscation.interfaces", "org.spongepowered.tools.obfuscation.mirror", "org.spongepowered.tools.obfuscation.struct", "org.spongepowered.tools.obfuscation.mapping"};
                Class<?> moduleClass = mixinModule.getClass();
                for (String pkg : criticalPackages) {
                    RuntimeModuleOpener.openPackage(mixinModule, pkg, moduleClass);
                    RuntimeModuleOpener.exportPackage(mixinModule, pkg, moduleClass);
                    if (currentModule != null) {
                        RuntimeModuleOpener.openPackageToModule(mixinModule, pkg, currentModule, moduleClass);
                        RuntimeModuleOpener.exportPackageToModule(mixinModule, pkg, currentModule, moduleClass);
                    }
                    if (ecjModule == null) continue;
                    RuntimeModuleOpener.openPackageToModule(mixinModule, pkg, ecjModule, moduleClass);
                    RuntimeModuleOpener.exportPackageToModule(mixinModule, pkg, ecjModule, moduleClass);
                }
                RuntimeModuleOpener.tryUnsafeOpen(mixinModule, criticalPackages);
                RuntimeModuleOpener.verifyAccess();
                initialized = true;
            }
            catch (Exception e) {
                LOGGER.error("[RuntimeModuleOpener] Failed to open modules", (Throwable)e);
                LOGGER.error("Stacktrace:", (Throwable)e);
                initialized = true;
            }
        }
    }

    private static Object getECJModule() {
        try {
            String[] ecjClasses;
            for (String className : ecjClasses = new String[]{"net.rain.repack.ecj.internal.compiler.tool.EclipseCompiler", "net.rain.repack.ecj.internal.compiler.Compiler", "net.rain.repack.ecj.internal.compiler.batch.Main", "net.rain.repack.ecj.internal.compiler.apt.dispatch.BatchAnnotationProcessorManager"}) {
                try {
                    Class<?> ecjClass = Class.forName(className);
                    Object module = RuntimeModuleOpener.getModuleOf(ecjClass);
                    if (module == null) continue;
                    String moduleName = RuntimeModuleOpener.getModuleName(module);
                    return module;
                }
                catch (ClassNotFoundException ecjClass) {
                    // empty catch block
                }
            }
            try {
                Class<?> moduleLayerClass = Class.forName("java.lang.ModuleLayer");
                Object bootLayer = moduleLayerClass.getMethod("boot", new Class[0]).invoke(null, new Object[0]);
                Set modules = (Set)bootLayer.getClass().getMethod("modules", new Class[0]).invoke(bootLayer, new Object[0]);
                for (Object module : modules) {
                    String name = RuntimeModuleOpener.getModuleName(module);
                    if (!"ecj".equals(name) && !name.contains("eclipse.jdt")) continue;
                    return module;
                }
            }
            catch (Exception e) {
                LOGGER.debug("[RuntimeModuleOpener] ModuleLayer lookup failed: {}", (Object)e.getMessage());
            }
        }
        catch (Exception e) {
            LOGGER.warn("[RuntimeModuleOpener] Failed to detect ECJ module: {}", (Object)e.getMessage());
        }
        return null;
    }

    private static String getModuleName(Object module) {
        try {
            Method getName = module.getClass().getMethod("getName", new Class[0]);
            return (String)getName.invoke(module, new Object[0]);
        }
        catch (Exception e) {
            return "unknown";
        }
    }

    private static void openPackageToModule(Object sourceModule, String pkg, Object targetModule, Class<?> moduleClass) {
        try {
        block8: {
            Method getPackagesMethod = moduleClass.getMethod("getPackages", new Class[0]);
            Set packages = (Set)getPackagesMethod.invoke(sourceModule, new Object[0]);
            if (!packages.contains(pkg)) {
                LOGGER.trace("[RuntimeModuleOpener] Package {} not in module, skipping", (Object)pkg);
                return;
            }
            Method isOpenMethod = moduleClass.getMethod("isOpen", String.class, moduleClass);
            boolean isOpen = (Boolean)isOpenMethod.invoke(sourceModule, pkg, targetModule);
            if (!isOpen) break block8;
            LOGGER.trace("[RuntimeModuleOpener] Package {} already open to {}", (Object)pkg, (Object)RuntimeModuleOpener.getModuleName(targetModule));
            return;
        }
        }
        catch (Exception e4) {
            LOGGER.debug("[RuntimeModuleOpener] Could not open package {}: {}", (Object)pkg, (Object)e4.getMessage());
            return;
        }
        try {
            Method addOpens = moduleClass.getMethod("addOpens", String.class, moduleClass);
            addOpens.invoke(sourceModule, pkg, targetModule);
            return;
        }
        catch (Exception e) {
            try {
                LOGGER.debug("Standard addOpens failed: {}", (Object)e.getMessage());
                try {
                    Method implAddOpens = moduleClass.getDeclaredMethod("implAddOpens", String.class, moduleClass);
                    implAddOpens.setAccessible(true);
                    implAddOpens.invoke(sourceModule, pkg, targetModule);
                }
                catch (Exception e2) {
                    LOGGER.debug("implAddOpens failed: {}", (Object)e2.getMessage());
                }
            }
            catch (Exception e3) {
                LOGGER.debug("[RuntimeModuleOpener] Could not open package {}: {}", (Object)pkg, (Object)e3.getMessage());
            }
        }
    }

    private static void exportPackageToModule(Object sourceModule, String pkg, Object targetModule, Class<?> moduleClass) {
        try {
        block8: {
            Method getPackagesMethod = moduleClass.getMethod("getPackages", new Class[0]);
            Set packages = (Set)getPackagesMethod.invoke(sourceModule, new Object[0]);
            if (!packages.contains(pkg)) {
                return;
            }
            Method isExportedMethod = moduleClass.getMethod("isExported", String.class, moduleClass);
            boolean isExported = (Boolean)isExportedMethod.invoke(sourceModule, pkg, targetModule);
            if (!isExported) break block8;
            return;
        }
        }
        catch (Exception e4) {
            LOGGER.debug("[RuntimeModuleOpener] Could not export package {}: {}", (Object)pkg, (Object)e4.getMessage());
            return;
        }
        try {
            Method addExports = moduleClass.getMethod("addExports", String.class, moduleClass);
            addExports.invoke(sourceModule, pkg, targetModule);
            return;
        }
        catch (Exception e) {
            try {
                LOGGER.debug("Standard addExports failed: {}", (Object)e.getMessage());
                try {
                    Method implAddExports = moduleClass.getDeclaredMethod("implAddExports", String.class, moduleClass);
                    implAddExports.setAccessible(true);
                    implAddExports.invoke(sourceModule, pkg, targetModule);
                }
                catch (Exception e2) {
                    LOGGER.debug("implAddExports failed: {}", (Object)e2.getMessage());
                }
            }
            catch (Exception e3) {
                LOGGER.debug("[RuntimeModuleOpener] Could not export package {}: {}", (Object)pkg, (Object)e3.getMessage());
            }
        }
    }

    private static Object getModuleOf(Class<?> clazz) {
        try {
            Method getModule = Class.class.getMethod("getModule", new Class[0]);
            Object module = getModule.invoke(clazz, new Object[0]);
            Method isNamed = module.getClass().getMethod("isNamed", new Class[0]);
            boolean named = (Boolean)isNamed.invoke(module, new Object[0]);
            if (named) {
                Method getName = module.getClass().getMethod("getName", new Class[0]);
                String name = (String)getName.invoke(module, new Object[0]);
                return module;
            }
            return module;
        }
        catch (Exception e) {
            LOGGER.debug("Could not get module for {}", (Object)clazz.getName());
            return null;
        }
    }

    private static void openPackage(Object module, String pkg, Class<?> moduleClass) {
        try {
            Method getPackagesMethod = moduleClass.getMethod("getPackages", new Class[0]);
            Set packages = (Set)getPackagesMethod.invoke(module, new Object[0]);
            if (!packages.contains(pkg)) {
                return;
            }
            try {
                Method implAddOpens = moduleClass.getDeclaredMethod("implAddOpens", String.class);
                implAddOpens.setAccessible(true);
                implAddOpens.invoke(module, pkg);
                return;
            }
            catch (NoSuchMethodException implAddOpens) {
                try {
                    Method implAddOpensToAllUnnamed = moduleClass.getDeclaredMethod("implAddOpensToAllUnnamed", String.class);
                    implAddOpensToAllUnnamed.setAccessible(true);
                    implAddOpensToAllUnnamed.invoke(module, pkg);
                    return;
                }
                catch (NoSuchMethodException implAddOpensToAllUnnamed) {
                    try {
                        Method addOpens = moduleClass.getMethod("addOpens", String.class, moduleClass);
                        Object targetModule = RuntimeModuleOpener.getModuleOf(RuntimeModuleOpener.class);
                        addOpens.invoke(module, pkg, targetModule);
                    }
                    catch (Exception e) {
                        LOGGER.debug("Could not open package {}: {}", (Object)pkg, (Object)e.getMessage());
                    }
                }
            }
        }
        catch (Exception e) {
            LOGGER.debug("Failed to open package {}: {}", (Object)pkg, (Object)e.getMessage());
        }
    }

    private static void exportPackage(Object module, String pkg, Class<?> moduleClass) {
        try {
            Method getPackagesMethod = moduleClass.getMethod("getPackages", new Class[0]);
            Set packages = (Set)getPackagesMethod.invoke(module, new Object[0]);
            if (!packages.contains(pkg)) {
                return;
            }
            try {
                Method implAddExports = moduleClass.getDeclaredMethod("implAddExports", String.class);
                implAddExports.setAccessible(true);
                implAddExports.invoke(module, pkg);
                return;
            }
            catch (NoSuchMethodException implAddExports) {
                try {
                    Method implAddExportsToAllUnnamed = moduleClass.getDeclaredMethod("implAddExportsToAllUnnamed", String.class);
                    implAddExportsToAllUnnamed.setAccessible(true);
                    implAddExportsToAllUnnamed.invoke(module, pkg);
                    return;
                }
                catch (NoSuchMethodException implAddExportsToAllUnnamed) {
                    try {
                        Method addExports = moduleClass.getMethod("addExports", String.class, moduleClass);
                        Object targetModule = RuntimeModuleOpener.getModuleOf(RuntimeModuleOpener.class);
                        addExports.invoke(module, pkg, targetModule);
                    }
                    catch (Exception e) {
                        LOGGER.debug("Could not export package {}: {}", (Object)pkg, (Object)e.getMessage());
                    }
                }
            }
        }
        catch (Exception e) {
            LOGGER.debug("Failed to export package {}: {}", (Object)pkg, (Object)e.getMessage());
        }
    }

    private static void tryUnsafeOpen(Object module, String[] packages) {
        try {
            Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            Unsafe unsafe = (Unsafe)unsafeField.get(null);
            Class<?> moduleClass = module.getClass();
            try {
                Field openPackagesField = moduleClass.getDeclaredField("openPackages");
                long offset = unsafe.objectFieldOffset(openPackagesField);
                Object openPackages = unsafe.getObject(module, offset);
                if (openPackages instanceof Map) {
                    Map map = (Map)openPackages;
                    for (String pkg : packages) {
                        if (map.containsKey(pkg)) continue;
                        map.put(pkg, new HashSet());
                    }
                }
            }
            catch (NoSuchFieldException e) {
                LOGGER.debug("openPackages field not found");
            }
        }
        catch (Exception e) {
            LOGGER.debug("Unsafe method failed: {}", (Object)e.getMessage());
        }
    }

    private static void verifyAccess() {
        String[] testClasses = new String[]{"org.spongepowered.tools.obfuscation.AnnotatedMixins", "org.spongepowered.tools.obfuscation.MixinObfuscationProcessorTargets", "org.spongepowered.tools.obfuscation.MixinObfuscationProcessorInjection", "org.spongepowered.asm.mixin.transformer.MixinTransformer", "org.spongepowered.asm.mixin.Mixin"};
        int successCount = 0;
        for (String className : testClasses) {
            try {
                Class<?> clazz = Class.forName(className);
                Method[] methods = clazz.getDeclaredMethods();
                ++successCount;
            }
            catch (Exception e) {
                LOGGER.warn("[RuntimeModuleOpener]   \u274c Cannot access: {} - {}", (Object)className, (Object)e.getMessage());
            }
        }
    }

    public static void reset() {
        initialized = false;
        javaBaseOpened = false;
    }
}

