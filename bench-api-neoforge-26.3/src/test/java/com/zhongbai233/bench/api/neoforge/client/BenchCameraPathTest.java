package com.zhongbai233.bench.api.neoforge.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BenchCameraPathTest {
    private static final BenchClientPose ORIGIN = new BenchClientPose(0.0, 64.0, 0.0, 0.0F, 0.0F);

    @Test
    void samplesLinearSegmentAtTickGranularity() {
        BenchCameraPath path = BenchCameraPath.from(ORIGIN)
                .to(new BenchClientPose(10.0, 74.0, 20.0, 90.0F, -30.0F), 10)
                .build();

        assertEquals(10, path.totalDurationTicks());
        assertEquals(ORIGIN, path.sampleAt(0));
        BenchClientPose middle = path.sampleAt(5);
        assertEquals(5.0, middle.x(), 1.0E-9);
        assertEquals(69.0, middle.y(), 1.0E-9);
        assertEquals(10.0, middle.z(), 1.0E-9);
        assertEquals(45.0F, middle.yaw(), 1.0E-5F);
        assertEquals(-15.0F, middle.pitch(), 1.0E-5F);
        assertEquals(10.0, path.sampleAt(10).x(), 1.0E-9);
    }

    @Test
    void rotatesAlongTheShortestArc() {
        BenchCameraPath path = BenchCameraPath.from(new BenchClientPose(0.0, 64.0, 0.0, 350.0F, 0.0F))
                .to(new BenchClientPose(0.0, 64.0, 0.0, 10.0F, 0.0F), 10)
                .build();

        // 350 -> 10 must travel +20 degrees through 360, not -340 the long way around.
        assertEquals(360.0F, path.sampleAt(5).yaw(), 1.0E-4F);
    }

    @Test
    void easingChangesTheProgressButNotTheEndpoints() {
        BenchCameraPath path = BenchCameraPath.from(ORIGIN)
                .to(new BenchClientPose(10.0, 64.0, 0.0, 0.0F, 0.0F), 10, BenchEasing.EASE_IN)
                .build();

        assertEquals(0.0, path.sampleAt(0).x(), 1.0E-9);
        assertEquals(2.5, path.sampleAt(5).x(), 1.0E-9);
        assertEquals(10.0, path.sampleAt(10).x(), 1.0E-9);
    }

    @Test
    void derivesDurationFromAConstantSpeed() {
        BenchCameraPath path = BenchCameraPath.from(ORIGIN)
                .toAtSpeed(new BenchClientPose(0.0, 64.0, 30.0, 0.0F, 0.0F), 10.0)
                .build();

        // 30 blocks at 10 blocks per second is 3 seconds, which is 60 client ticks.
        assertEquals(60, path.totalDurationTicks());
        assertEquals(15.0, path.sampleAt(30).z(), 1.0E-9);
    }

    @Test
    void onceStopsAtTheLastKeyframe() {
        BenchCameraPath path = BenchCameraPath.from(ORIGIN)
                .to(new BenchClientPose(10.0, 64.0, 0.0, 0.0F, 0.0F), 10)
                .build();

        assertFalse(path.isFinished(9));
        assertTrue(path.isFinished(10));
        assertEquals(10.0, path.sampleAt(25).x(), 1.0E-9);
    }

    @Test
    void loopRestartsAndPingPongReverses() {
        BenchClientPose end = new BenchClientPose(10.0, 64.0, 0.0, 0.0F, 0.0F);
        BenchCameraPath loop = BenchCameraPath.from(ORIGIN).to(end, 10).mode(BenchCameraPathMode.LOOP).build();
        BenchCameraPath pingPong = BenchCameraPath.from(ORIGIN).to(end, 10).mode(BenchCameraPathMode.PING_PONG).build();

        assertFalse(loop.isFinished(1000));
        assertEquals(2.0, loop.sampleAt(12).x(), 1.0E-9);
        assertFalse(pingPong.isFinished(1000));
        assertEquals(8.0, pingPong.sampleAt(12).x(), 1.0E-9);
        assertEquals(0.0, pingPong.sampleAt(20).x(), 1.0E-9);
        assertEquals(2.0, pingPong.sampleAt(22).x(), 1.0E-9);
    }

    @Test
    void keyframeLookupMatchesOnlyExactTimelineOffsets() {
        BenchCameraPath path = BenchCameraPath.from(ORIGIN)
                .to(new BenchClientPose(10.0, 64.0, 0.0, 0.0F, 0.0F), 10)
                .hold(4).capture("still")
                .build();

        assertFalse(path.keyframeAt(10).capturesScreenshot());
        assertNull(path.keyframeAt(13));
        assertEquals("still", path.keyframeAt(14).captureName());
        assertTrue(path.keyframeAt(14).capturesScreenshot());
    }

    @Test
    void holdKeepsThePreviousPose() {
        BenchClientPose end = new BenchClientPose(10.0, 64.0, 0.0, 0.0F, 0.0F);
        BenchCameraPath path = BenchCameraPath.from(ORIGIN).to(end, 10).hold(5).build();

        assertEquals(15, path.totalDurationTicks());
        assertEquals(10.0, path.sampleAt(12).x(), 1.0E-9);
        assertEquals(10.0, path.sampleAt(15).x(), 1.0E-9);
    }

    @Test
    void smoothIsTheDefaultAndCanBeDisabled() {
        BenchClientPose end = new BenchClientPose(10.0, 64.0, 0.0, 0.0F, 0.0F);
        assertTrue(BenchCameraPath.from(ORIGIN).to(end, 5).build().smooth());
        assertFalse(BenchCameraPath.from(ORIGIN).to(end, 5).snapped().build().smooth());
    }

    @Test
    void rejectsDegenerateTimelines() {
        assertThrows(IllegalStateException.class, () -> BenchCameraPath.from(ORIGIN).build());
        assertThrows(IllegalArgumentException.class, () -> BenchCameraPath.from(ORIGIN).to(ORIGIN, 0));
        assertThrows(IllegalArgumentException.class, () -> BenchCameraPath.from(ORIGIN).toAtSpeed(ORIGIN, 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> BenchCameraPath.from(ORIGIN).to(ORIGIN, 5).capture(" ").build());
    }

    @Test
    void easingCurvesStayInsideTheUnitInterval() {
        for (BenchEasing easing : BenchEasing.values()) {
            assertEquals(0.0, easing.apply(0.0), 1.0E-9, easing.name());
            assertEquals(1.0, easing.apply(1.0), 1.0E-9, easing.name());
            assertEquals(0.0, easing.apply(-2.0), 1.0E-9, easing.name());
            assertEquals(1.0, easing.apply(7.0), 1.0E-9, easing.name());
        }
        assertSame(BenchEasing.LINEAR, BenchEasing.valueOf("LINEAR"));
        assertEquals(0.5, BenchEasing.EASE_IN_OUT.apply(0.5), 1.0E-9);
        assertEquals(0.5, BenchEasing.SMOOTH_STEP.apply(0.5), 1.0E-9);
    }
}
