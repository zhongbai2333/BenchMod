package com.zhongbai233.bench.runtime.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FrameStabilityMonitorTest {
    @Test
    void needsEnoughSamplesBeforeItCanAnswer() {
        FrameStabilityMonitor monitor = new FrameStabilityMonitor();
        record(monitor, 4, 1_000_000L);

        assertTrue(monitor.isStable(0));
        assertTrue(monitor.isStable(3));
        assertFalse(monitor.isStable(8));
        assertFalse(monitor.isStable(FrameStabilityMonitor.WINDOW + 1));
    }

    @Test
    void evenFramePacingIsStable() {
        FrameStabilityMonitor monitor = new FrameStabilityMonitor();
        record(monitor, 20, 8_000_000L);

        assertTrue(monitor.isStable(16));
    }

    @Test
    void aHitchInsideTheWindowIsNotStable() {
        FrameStabilityMonitor monitor = new FrameStabilityMonitor();
        record(monitor, 20, 8_000_000L);
        monitor.recordFrame(nanosAfter(20, 8_000_000L) + 400_000_000L);

        assertFalse(monitor.isStable(8));
    }

    @Test
    void stabilityReturnsOnceTheHitchLeavesTheWindow() {
        FrameStabilityMonitor monitor = new FrameStabilityMonitor();
        long clock = 0L;
        monitor.recordFrame(clock);
        clock += 400_000_000L;
        monitor.recordFrame(clock);
        for (int i = 0; i < 12; i++) {
            clock += 8_000_000L;
            monitor.recordFrame(clock);
        }

        assertTrue(monitor.isStable(8));
        assertFalse(monitor.isStable(13));
    }

    private static void record(FrameStabilityMonitor monitor, int frames, long intervalNanos) {
        for (int i = 0; i <= frames; i++) monitor.recordFrame(i * intervalNanos);
    }

    private static long nanosAfter(int frames, long intervalNanos) {
        return frames * intervalNanos;
    }
}
