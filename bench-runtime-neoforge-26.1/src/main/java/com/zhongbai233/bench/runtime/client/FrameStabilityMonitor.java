package com.zhongbai233.bench.runtime.client;

/**
 * Tracks the newest frame intervals so a capture can wait until the picture stopped hitching.
 *
 * <p>The window is small and fixed, so the render thread never allocates while recording.
 */
public final class FrameStabilityMonitor {
    /** Number of intervals kept; also the largest window {@link #isStable(int)} can answer for. */
    public static final int WINDOW = 32;

    /** Default multiple of the fastest interval the slowest may reach and still count as stable. */
    public static final double DEFAULT_STABLE_RATIO = 2.0;

    private final double stableRatio;
    private final long[] intervals = new long[WINDOW];
    private int count;
    private int next;
    private long previousFrameNanos = -1;

    public FrameStabilityMonitor() {
        this(DEFAULT_STABLE_RATIO);
    }

    public FrameStabilityMonitor(double stableRatio) {
        if (!(stableRatio >= 1.0)) throw new IllegalArgumentException("stableRatio must be at least 1.0");
        this.stableRatio = stableRatio;
    }

    public void recordFrame(long timestampNanos) {
        if (previousFrameNanos >= 0) {
            long interval = timestampNanos - previousFrameNanos;
            if (interval > 0) {
                intervals[next] = interval;
                next = (next + 1) % WINDOW;
                if (count < WINDOW) count++;
            }
        }
        previousFrameNanos = timestampNanos;
    }

    /** Returns {@code true} when the newest {@code required} intervals stay inside the stable ratio. */
    public boolean isStable(int required) {
        if (required <= 0) return true;
        if (required > WINDOW || count < required) return false;
        long min = Long.MAX_VALUE;
        long max = 0;
        for (int i = 1; i <= required; i++) {
            long interval = intervals[Math.floorMod(next - i, WINDOW)];
            min = Math.min(min, interval);
            max = Math.max(max, interval);
        }
        return max <= min * stableRatio;
    }

    public int recordedIntervals() {
        return count;
    }
}
