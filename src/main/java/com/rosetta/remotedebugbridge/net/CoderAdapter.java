package com.rosetta.remotedebugbridge.net;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Cross-class-loader adapter for the Coder plugin API ({@code dev.codestuff.coder.api.CoderAPI}).
 *
 * The Coder plugin lives in its own Bukkit {@code PluginClassLoader}; the bridge only sees
 * the mod class loader, so every touch goes through the plugin's own loader via reflection.
 * Exposed through the remote bridge command {@code coder}:
 *
 *   {"cmd":"coder","args":{"action":"list"}}
 *   {"cmd":"coder","args":{"action":"api","method":"getMinecraftVersion","args":[]}}
 *   {"cmd":"coder","args":{"action":"run","file":"smoke.java"}}
 */
public final class CoderAdapter {

    private static final Logger LOGGER = LogManager.getLogger("RosettaNexus/Coder");
    private static final String PLUGIN_NAME = "Coder";
    private static final String API_CLASS = "dev.codestuff.coder.api.CoderAPI";

    private CoderAdapter() {
    }

    /** Lists the CoderAPI surface (plugin version, method count, singleton availability). */
    public static JsonObject list() throws Exception {
        Class<?> api = apiClass();
        Object instance = apiInstance(api);
        JsonObject out = new JsonObject();
        out.addProperty("plugin", PLUGIN_NAME);
        out.addProperty("version", pluginVersion());
        out.addProperty("apiClass", api.getName());
        out.addProperty("loader", String.valueOf(api.getClassLoader()));
        out.addProperty("methods", api.getMethods().length);
        out.addProperty("instance", instance != null);
        return out;
    }

    /**
     * Invokes a CoderAPI method by name. Instance methods are called on the CoderAPI
     * singleton, static methods directly. Arguments follow the same typed-JSON format
     * as the {@code reflect} command.
     */
    public static JsonObject invoke(String methodName, JsonArray input) throws Throwable {
        Class<?> api = apiClass();
        Object[] values = convertArgs(input == null ? new JsonArray() : input);
        Method chosen;
        try {
            chosen = Reflect.findMethod(api, methodName, values, false);
        } catch (NoSuchMethodException instanceMissing) {
            chosen = Reflect.findMethod(api, methodName, values, true);
        }
        Object target = Modifier.isStatic(chosen.getModifiers()) ? null : apiInstance(api);
        long start = System.currentTimeMillis();
        Object result = chosen.invoke(target, values);
        JsonObject out = new JsonObject();
        out.addProperty("method", methodName);
        out.addProperty("returns", chosen.getReturnType().getName());
        out.addProperty("result", result == null ? "null" : String.valueOf(result));
        out.addProperty("ms", System.currentTimeMillis() - start);
        return out;
    }

    // ------------------------------------------------------------------ internals

    private static Class<?> apiClass() throws Exception {
        ClassLoader loader = BukkitAdapter.pluginClassLoader(PLUGIN_NAME);
        return Class.forName(API_CLASS, true, loader);
    }

    private static Object apiInstance(Class<?> api) throws Exception {
        try {
            Method getInstance = api.getMethod("getInstance");
            return getInstance.invoke(null);
        } catch (NoSuchMethodException missing) {
            return null;
        }
    }

    private static String pluginVersion() {
        try {
            Object plugin = Reflect.call(BukkitAdapter.pluginManager(), "getPlugin", PLUGIN_NAME);
            Object description = Reflect.call(plugin, "getDescription");
            return String.valueOf(Reflect.call(description, "getVersion"));
        } catch (Throwable error) {
            LOGGER.debug("could not read Coder version: {}", error.toString());
            return "unknown";
        }
    }

    private static Object[] convertArgs(JsonArray input) {
        Object[] values = new Object[input.size()];
        for (int i = 0; i < input.size(); i++) {
            JsonElement element = input.get(i);
            if (!element.isJsonObject()) {
                if (element.isJsonNull()) {
                    values[i] = null;
                } else if (element.getAsJsonPrimitive().isBoolean()) {
                    values[i] = element.getAsBoolean();
                } else if (element.getAsJsonPrimitive().isNumber()) {
                    Number number = element.getAsNumber();
                    double asDouble = number.doubleValue();
                    if (asDouble == Math.rint(asDouble) && !element.toString().contains(".")) {
                        long asLong = number.longValue();
                        values[i] = (asLong >= Integer.MIN_VALUE && asLong <= Integer.MAX_VALUE)
                                ? (Object) (int) asLong : (Object) asLong;
                    } else {
                        values[i] = asDouble;
                    }
                } else {
                    values[i] = element.getAsString();
                }
                continue;
            }
            JsonObject spec = element.getAsJsonObject();
            String type = spec.has("type") ? spec.get("type").getAsString() : "string";
            String value = spec.has("value") && !spec.get("value").isJsonNull() ? spec.get("value").getAsString() : null;
            values[i] = switch (type) {
                case "string" -> value;
                case "int" -> Integer.parseInt(value);
                case "long" -> Long.parseLong(value);
                case "double" -> Double.parseDouble(value);
                case "float" -> Float.parseFloat(value);
                case "boolean" -> Boolean.parseBoolean(value);
                case "short" -> Short.parseShort(value);
                case "byte" -> Byte.parseByte(value);
                case "char" -> value == null || value.isEmpty() ? '\0' : value.charAt(0);
                case "null" -> null;
                default -> throw new IllegalArgumentException("bad arg type: " + type);
            };
        }
        return values;
    }
}
