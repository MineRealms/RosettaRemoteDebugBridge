/*
 * Decompiled with CFR 0.152.
 */
package net.rain.eventbus;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(value=RetentionPolicy.RUNTIME)
@Target(value={ElementType.METHOD})
public @interface RainSubscribeEvent {
    public EventPriority priority() default EventPriority.NORMAL;

    public boolean receiveCanceled() default false;

    public static enum EventPriority {
        HIGHEST(0),
        HIGH(1),
        NORMAL(2),
        LOW(3),
        LOWEST(4),
        MONITOR(5);

        public final int order;

        private EventPriority(int order) {
            this.order = order;
        }
    }
}

