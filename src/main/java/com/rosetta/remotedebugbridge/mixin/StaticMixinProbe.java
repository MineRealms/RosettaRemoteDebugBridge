package com.rosetta.remotedebugbridge.mixin;

import com.rosetta.remotedebugbridge.RosettaRemoteDebugBridge;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Runtime state for the P4 static Mixin probe.
 *
 * <p>This class deliberately lives outside the mixin config package
 * ({@code ...mixin.staticprobe}). ModLauncher excludes packages owned by a mixin
 * config from regular class loading, so a helper referenced from injected target
 * code must be resolved from a normal package of the mod.</p>
 */
public final class StaticMixinProbe {

    private static final AtomicLong CALLS = new AtomicLong();
    private static final AtomicLong LAST_LOG_MILLIS = new AtomicLong();

    private StaticMixinProbe() {
    }

    public static void onChickenAiStep(Object chicken) {
        long calls = CALLS.incrementAndGet();
        long now = System.currentTimeMillis();
        long last = LAST_LOG_MILLIS.get();
        if (calls <= 3L || now - last >= 2000L) {
            LAST_LOG_MILLIS.set(now);
            RosettaRemoteDebugBridge.LOGGER.info(
                    "[P4-STATIC-MIXIN] Chicken.aiStep (m_8107_) tick #{} entity={} - static mixin is live",
                    calls, chicken);
        }
    }

    public static long callCount() {
        return CALLS.get();
    }
}
