package com.zhongbai233.bench.api.neoforge.client;

import java.util.List;

/**
 * Tracks whether the client environment stayed good enough for a comparable measurement.
 *
 * <p>Window focus loss, minimize, pause, an opened screen and window resizes all make frame timings
 * incomparable. When any of them happens the Runtime reports the run as {@code INCONCLUSIVE} rather
 * than {@code PASSED}, so a degraded machine never looks like a successful measurement.
 */
public interface BenchClientEnvironment {
    /** Returns {@code true} while no invalidating condition has been observed. */
    boolean isValid();

    /** Returns every invalidation observed so far, in observation order. */
    List<String> invalidations();

    /** Records a scenario-specific invalidation. */
    void invalidate(String reason);

    /** Returns the current readiness snapshot of the render pipeline. */
    BenchClientReadiness readiness();

    /** Returns {@code true} when the newest frame intervals were comparable to each other. */
    boolean isFrameStable(int requiredFrames);
}
