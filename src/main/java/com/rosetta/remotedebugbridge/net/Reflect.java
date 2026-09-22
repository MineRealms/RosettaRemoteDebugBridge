package com.rosetta.remotedebugbridge.net;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Tiny reflection helper used by the Bukkit adapter.
 *
 * The whole adapter is reflection based so the mod keeps zero compile-time and
 * zero linkage dependency on Bukkit. That means the same mod jar can be dropped on a
 * pure Forge server (adapter inert, TCP bridge still up) and on Mohist (adapter active).
 */
final class Reflect {

    private Reflect() {
    }

    static Class<?> load(String name) throws ClassNotFoundException {
        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        if (tccl != null) {
            try {
                return Class.forName(name, false, tccl);
            } catch (ClassNotFoundException ignored) {
            }
        }
        ClassLoader own = Reflect.class.getClassLoader();
        if (own != null) {
            try {
                return Class.forName(name, false, own);
            } catch (ClassNotFoundException ignored) {
            }
        }
        return Class.forName(name);
    }

    static Object callStatic(Class<?> type, String name, Object... args) throws Exception {
        Method method = findMethod(type, name, args, true);
        return method.invoke(null, args);
    }

    static Object call(Object target, String name, Object... args) throws Exception {
        if (target == null) {
            throw new NullPointerException("target is null for call " + name);
        }
        Method method = findMethod(target.getClass(), name, args, false);
        return method.invoke(target, args);
    }

    static Method findMethod(Class<?> type, String name, Object[] args, boolean staticOnly) throws NoSuchMethodException {
        Object[] actual = args == null ? new Object[0] : args;
        Method best = null;
        int bestScore = -1;
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (!method.getName().equals(name) || method.getParameterCount() != actual.length) {
                    continue;
                }
                if (Modifier.isStatic(method.getModifiers()) != staticOnly) {
                    continue;
                }
                int score = score(method.getParameterTypes(), actual);
                if (score > bestScore) {
                    bestScore = score;
                    best = method;
                }
            }
        }
        if (best == null) {
            // interface/default declarations
            for (Method method : type.getMethods()) {
                if (!method.getName().equals(name) || method.getParameterCount() != actual.length) {
                    continue;
                }
                if (Modifier.isStatic(method.getModifiers()) != staticOnly) {
                    continue;
                }
                int score = score(method.getParameterTypes(), actual);
                if (score > bestScore) {
                    bestScore = score;
                    best = method;
                }
            }
        }
        if (best == null) {
            StringBuilder types = new StringBuilder();
            for (Object arg : actual) {
                types.append(types.length() == 0 ? "" : ",").append(arg == null ? "null" : arg.getClass().getName());
            }
            throw new NoSuchMethodException(type.getName() + "#" + name + "(" + types + ")");
        }
        best.setAccessible(true);
        return best;
    }

    static Object field(Object target, String name) throws Exception {
        if (target == null) {
            throw new NullPointerException("target is null for field " + name);
        }
        for (Class<?> current = target.getClass(); current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException(target.getClass().getName() + "#" + name);
    }

    private static int score(Class<?>[] params, Object[] args) {
        int score = 0;
        for (int i = 0; i < params.length; i++) {
            Class<?> param = params[i];
            Object arg = args[i];
            if (arg == null) {
                if (param.isPrimitive()) {
                    return -1;
                }
                score += 1;
                continue;
            }
            Class<?> actual = arg.getClass();
            if (param.equals(actual)) {
                score += 4;
            } else if (param.isAssignableFrom(actual)) {
                score += 3;
            } else if (isUnboxingCompatible(param, actual)) {
                score += 2;
            } else {
                return -1;
            }
        }
        return score;
    }

    private static boolean isUnboxingCompatible(Class<?> param, Class<?> argType) {
        if (!param.isPrimitive()) {
            return false;
        }
        return (param == int.class && argType == Integer.class)
                || (param == long.class && argType == Long.class)
                || (param == double.class && argType == Double.class)
                || (param == float.class && argType == Float.class)
                || (param == boolean.class && argType == Boolean.class)
                || (param == short.class && argType == Short.class)
                || (param == byte.class && argType == Byte.class)
                || (param == char.class && argType == Character.class);
    }
}
