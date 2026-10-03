package com.zhongbai233.bench.api.neoforge.client;

/** Absolute player pose used by deterministic client benchmark scripts. */
public record BenchClientPose(double x, double y, double z, float yaw, float pitch) {
    /** A pose standing at the eye position and looking at an absolute world target. */
    public static BenchClientPose lookingAt(double x, double y, double z,
                                            double targetX, double targetY, double targetZ) {
        double dx = targetX - x;
        double dy = targetY - y;
        double dz = targetZ - z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));
        return new BenchClientPose(x, y, z, yaw, Math.max(-90.0F, Math.min(90.0F, pitch)));
    }
}