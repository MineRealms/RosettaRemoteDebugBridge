package com.rosetta.remotedebugbridge.debug.client;

import com.google.gson.JsonObject;
import com.rosetta.remotedebugbridge.script.CompiledClass;
import com.rosetta.remotedebugbridge.script.DynamicClassLoader;
import com.rosetta.remotedebugbridge.script.JavaSourceCompiler;
import java.lang.reflect.Method;
import java.util.Map;
import net.rain.repack.ecj.internal.compiler.tool.EclipseCompiler;

/**
 * Client-side ECJ compile + execute for the SCRIPT permission tier.
 * Invoked only after an explicit per-use confirmation.
 */
public final class ClientScriptRunner {
    private ClientScriptRunner() {
    }

    public static JsonObject run(String source, String name) {
        JsonObject out = new JsonObject();
        if (source == null || source.isBlank()) {
            out.addProperty("ok", false);
            out.addProperty("error", "empty script source");
            return out;
        }
        try {
            String className = extractClassName(source, name);
            if (className == null || className.isBlank()) {
                out.addProperty("ok", false);
                out.addProperty("error", "could not extract class name");
                return out;
            }
            JavaSourceCompiler compiler = new JavaSourceCompiler(new EclipseCompiler());
            CompiledClass compiled = compiler.compileFromString(className, source);
            DynamicClassLoader loader = new DynamicClassLoader(Thread.currentThread().getContextClassLoader());
            if (compiled.allClasses != null) {
                for (Map.Entry<String, byte[]> entry : compiled.allClasses.entrySet()) {
                    loader.addCompiledClass(entry.getKey(), entry.getValue());
                }
            }
            loader.addCompiledClass(compiled.className, compiled.bytecode);
            Class<?> clazz = loader.loadClass(compiled.className);
            Method entry = null;
            for (String candidate : new String[]{"init", "initialize", "onLoad", "load", "register"}) {
                try {
                    Method method = clazz.getDeclaredMethod(candidate);
                    if (java.lang.reflect.Modifier.isStatic(method.getModifiers())) {
                        entry = method;
                        break;
                    }
                }
                catch (NoSuchMethodException ignored) {
                }
            }
            if (entry == null) {
                out.addProperty("ok", false);
                out.addProperty("error", "no public static init() found in " + compiled.className);
                return out;
            }
            Object result = entry.invoke(null);
            out.addProperty("ok", true);
            out.addProperty("class", compiled.className);
            out.addProperty("result", String.valueOf(result));
            return out;
        }
        catch (Throwable t) {
            out.addProperty("ok", false);
            out.addProperty("error", t.toString());
            return out;
        }
    }

    private static String extractClassName(String source, String name) {
        String clean = source.replaceAll("//.*", "").replaceAll("/\\*.*?\\*/", "");
        String pkg = "";
        int packageIndex = clean.indexOf("package ");
        if (packageIndex >= 0) {
            int end = clean.indexOf(';', packageIndex);
            if (end > packageIndex) {
                pkg = clean.substring(packageIndex + 8, end).trim();
            }
        }
        for (String keyword : new String[]{"public class ", "class ", "public final class ", "final class "}) {
            int index = clean.indexOf(keyword);
            if (index < 0) {
                continue;
            }
            String after = clean.substring(index + keyword.length()).trim();
            int end = 0;
            while (end < after.length() && (Character.isJavaIdentifierPart(after.charAt(end)))) {
                end++;
            }
            if (end > 0) {
                String simple = after.substring(0, end);
                return pkg.isEmpty() ? simple : pkg + "." + simple;
            }
        }
        return name;
    }
}
