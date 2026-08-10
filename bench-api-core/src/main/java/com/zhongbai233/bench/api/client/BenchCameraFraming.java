package com.zhongbai233.bench.api.client;

import java.util.Objects;

/**
 * Requests a camera pose that contains the complete target bounds.
 *
 * @param directionX world-space direction from the camera toward the target
 * @param directionY world-space direction from the camera toward the target
 * @param directionZ world-space direction from the camera toward the target
 * @param frameFill  desired maximum projected span, from {@code 0.05} to {@code 0.98}
 */
public record BenchCameraFraming(
        BenchBounds3 bounds, double directionX, double directionY, double directionZ, double frameFill) {
    public BenchCameraFraming {
        Objects.requireNonNull(bounds, "bounds");
        if (!Double.isFinite(directionX) || !Double.isFinite(directionY) || !Double.isFinite(directionZ)) {
            throw new IllegalArgumentException("View direction must be finite");
        }
        double scale = Math.max(Math.abs(directionX), Math.max(Math.abs(directionY), Math.abs(directionZ)));
        if (!(scale > 0.0)) throw new IllegalArgumentException("View direction must not be zero");
        directionX /= scale;
        directionY /= scale;
        directionZ /= scale;
        double length = Math.sqrt(directionX * directionX + directionY * directionY + directionZ * directionZ);
        directionX /= length;
        directionY /= length;
        directionZ /= length;
        if (!Double.isFinite(directionX) || !Double.isFinite(directionY) || !Double.isFinite(directionZ)) {
            throw new IllegalArgumentException("Normalized view direction must be finite");
        }
        if (!Double.isFinite(frameFill) || frameFill < 0.05 || frameFill > 0.98) {
            throw new IllegalArgumentException("frameFill must be between 0.05 and 0.98");
        }
    }

    /** A three-quarter view that exposes depth better than a straight-on projection. */
    public static BenchCameraFraming threeQuarter(BenchBounds3 bounds, double frameFill) {
        return new BenchCameraFraming(bounds, 1.0, -0.35, 1.0, frameFill);
    }
}