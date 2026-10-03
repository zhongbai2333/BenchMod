package com.zhongbai233.bench.api.neoforge.client;

import java.util.Objects;

/**
 * One stop on a camera path.
 *
 * @param pose          the absolute pose reached at the end of this keyframe
 * @param durationTicks client ticks spent travelling from the previous keyframe into this one;
 *                      the first keyframe of a path always uses {@code 0}
 * @param easing        curve applied to the travel from the previous keyframe into this one
 * @param captureName   screenshot requested when this keyframe is reached, or {@code null}
 */
public record BenchCameraKeyframe(BenchClientPose pose, int durationTicks, BenchEasing easing, String captureName) {
    public BenchCameraKeyframe {
        Objects.requireNonNull(pose, "pose");
        Objects.requireNonNull(easing, "easing");
        if (durationTicks < 0) throw new IllegalArgumentException("durationTicks must not be negative");
        if (captureName != null && captureName.isBlank()) {
            throw new IllegalArgumentException("captureName must not be blank");
        }
    }

    public boolean capturesScreenshot() {
        return captureName != null;
    }
}
