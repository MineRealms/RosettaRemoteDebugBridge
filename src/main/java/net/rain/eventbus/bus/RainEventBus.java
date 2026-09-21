/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraftforge.eventbus.api.Event
 *  org.apache.logging.log4j.LogManager
 *  org.apache.logging.log4j.Logger
 */
package net.rain.eventbus.bus;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraftforge.eventbus.api.Event;
import net.rain.eventbus.RainSubscribeEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class RainEventBus {
    private static final Logger LOGGER = LogManager.getLogger((String)"RainEventBus");
    private final Map<Class<?>, List<MethodListener>> listenerMap = new ConcurrentHashMap();
    private final Set<Class<?>> registeredClasses = Collections.newSetFromMap(new ConcurrentHashMap());

    public void register(Class<?> clazz) {
        if (this.registeredClasses.contains(clazz)) {
            LOGGER.warn("[RainEventBus] Class already registered, skipping: {}", (Object)clazz.getName());
            return;
        }
        int count = 0;
        for (Method method : clazz.getDeclaredMethods()) {
            RainSubscribeEvent annotation = method.getAnnotation(RainSubscribeEvent.class);
            if (annotation == null) continue;
            if (!Modifier.isStatic(method.getModifiers())) {
                LOGGER.warn("[RainEventBus] @RainSubscribeEvent method must be static: {}.{}", (Object)clazz.getSimpleName(), (Object)method.getName());
                continue;
            }
            if (!Modifier.isPublic(method.getModifiers())) {
                LOGGER.warn("[RainEventBus] @RainSubscribeEvent method must be public: {}.{}", (Object)clazz.getSimpleName(), (Object)method.getName());
                continue;
            }
            if (method.getParameterCount() != 1) {
                LOGGER.warn("[RainEventBus] @RainSubscribeEvent method must have exactly 1 parameter: {}.{}", (Object)clazz.getSimpleName(), (Object)method.getName());
                continue;
            }
            Class<?> eventType = method.getParameterTypes()[0];
            RainSubscribeEvent.EventPriority priority = annotation.priority();
            boolean receiveCanceled = annotation.receiveCanceled();
            String debugName = clazz.getSimpleName() + "." + method.getName() + "(" + eventType.getSimpleName() + ") [" + priority.name() + (receiveCanceled ? ", receiveCanceled" : "") + "]";
            method.setAccessible(true);
            MethodListener listener = new MethodListener(clazz, method, priority, receiveCanceled, debugName);
            List<MethodListener> list = this.listenerMap.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList());
            list.add(listener);
            list.sort(Comparator.comparingInt(ml -> ml.priority().order));
            LOGGER.info("[RainEventBus] Registered: {}", (Object)debugName);
            ++count;
        }
        if (count > 0) {
            this.registeredClasses.add(clazz);
            LOGGER.info("[RainEventBus] Class {} registered with {} listener(s)", (Object)clazz.getSimpleName(), (Object)count);
        } else {
            LOGGER.warn("[RainEventBus] No valid @RainSubscribeEvent methods found in: {}", (Object)clazz.getSimpleName());
        }
    }

    public void unregister(Class<?> clazz) {
        this.listenerMap.values().forEach(list -> list.removeIf(ml -> ml.ownerClass() == clazz));
        this.registeredClasses.remove(clazz);
        LOGGER.info("[RainEventBus] Unregistered class: {}", (Object)clazz.getSimpleName());
    }

    public void unregisterAll() {
        this.listenerMap.clear();
        this.registeredClasses.clear();
        LOGGER.info("[RainEventBus] All listeners unregistered");
    }

    public void post(Object event) {
        if (event == null) {
            return;
        }
        LinkedHashSet<Class> visited = new LinkedHashSet<Class>();
        LinkedList queue = new LinkedList();
        queue.add(event.getClass());
        while (!queue.isEmpty()) {
            Class current = (Class)queue.poll();
            if (current == null || current == Object.class || visited.contains(current)) continue;
            visited.add(current);
            if (current.getSuperclass() != null) {
                queue.add(current.getSuperclass());
            }
            Collections.addAll(queue, current.getInterfaces());
        }
        for (Class type : visited) {
            List<MethodListener> listeners = this.listenerMap.get(type);
            if (listeners == null || listeners.isEmpty()) continue;
            for (MethodListener ml : listeners) {
                boolean isCanceled = RainEventBus.isCanceled(event);
                if (isCanceled && ml.priority() != RainSubscribeEvent.EventPriority.MONITOR && !ml.receiveCanceled()) continue;
                try {
                    ml.method().invoke(null, event);
                }
                catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    LOGGER.error("[RainEventBus] Error in listener {}: {}", (Object)ml.debugName(), (Object)cause.getMessage(), (Object)cause);
                }
            }
        }
    }

    private static boolean isCanceled(Object event) {
        if (event instanceof Event) {
            Event forgeEvent = (Event)event;
            return forgeEvent.isCanceled();
        }
        return false;
    }

    public Set<Class<?>> getRegisteredClasses() {
        return Collections.unmodifiableSet(this.registeredClasses);
    }

    public Set<Class<?>> getRegisteredEventTypes() {
        return Collections.unmodifiableSet(this.listenerMap.keySet());
    }

    public int getTotalListenerCount() {
        return this.listenerMap.values().stream().mapToInt(List::size).sum();
    }

    private record MethodListener(Class<?> ownerClass, Method method, RainSubscribeEvent.EventPriority priority, boolean receiveCanceled, String debugName) {
    }
}

