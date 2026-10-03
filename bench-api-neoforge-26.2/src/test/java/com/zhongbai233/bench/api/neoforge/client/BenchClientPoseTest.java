package com.zhongbai233.bench.api.neoforge.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BenchClientPoseTest {
    @Test
    void lookingAtComputesYawAndPitchTowardTheTarget() {
        BenchClientPose south = BenchClientPose.lookingAt(0, 64, 0, 0, 64, 10);
        assertEquals(0.0F, south.yaw(), 1.0E-4F);
        assertEquals(0.0F, south.pitch(), 1.0E-4F);

        BenchClientPose west = BenchClientPose.lookingAt(0, 64, 0, -10, 64, 0);
        assertEquals(90.0F, west.yaw(), 1.0E-4F);

        BenchClientPose up45 = BenchClientPose.lookingAt(0, 64, 0, 0, 74, 10);
        assertEquals(-45.0F, up45.pitch(), 1.0E-4F);
    }

    @Test
    void straightVerticalTargetsClampToNinetyDegrees() {
        assertEquals(-90.0F, BenchClientPose.lookingAt(0, 64, 0, 0, 100, 0).pitch(), 1.0E-4F);
        assertEquals(90.0F, BenchClientPose.lookingAt(0, 64, 0, 0, 0, 0).pitch(), 1.0E-4F);
    }
}
