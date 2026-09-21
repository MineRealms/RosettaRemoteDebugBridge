/*
 * Decompiled with CFR 0.152.
 */
package net.rain.eventbus;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(value=RetentionPolicy.RUNTIME)
@Target(value={ElementType.TYPE})
public @interface RainEventSubscriber {
    public Bus bus() default Bus.FORGE;

    public static enum Bus {
        FORGE,
        MOD;

    }
}

