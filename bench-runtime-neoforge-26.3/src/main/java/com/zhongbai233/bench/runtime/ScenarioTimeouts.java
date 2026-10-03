package com.zhongbai233.bench.runtime;

import java.time.Duration;

/** Resolves the per-phase tick budget a scenario actually gets. */
public final class ScenarioTimeouts {
    private static final int TICKS_PER_SECOND = 20;

    private ScenarioTimeouts() {}

    /**
     * A scenario's declared {@code phaseTimeout} can raise the global budget but never lower it,
     * so a long multi-stage measurement works without a global timeout change while short
     * scenarios keep the safety net the consumer configured.
     */
    public static long effectivePhaseTicks(long globalPhaseTimeoutTicks, Duration declaredPhaseTimeout) {
        long millis = declaredPhaseTimeout.toMillis();
        long declaredTicks = millis >= Long.MAX_VALUE / TICKS_PER_SECOND
                ? Long.MAX_VALUE
                : millis * TICKS_PER_SECOND / 1000;
        return Math.max(globalPhaseTimeoutTicks, declaredTicks);
    }
}
