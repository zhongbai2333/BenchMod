package com.zhongbai233.bench.api.client;

/** Pure camera-framing mathematics, independent of Minecraft and the active renderer. */
public final class BenchCameraFramer {
    private static final double NEAR_PADDING = 0.25;

    private BenchCameraFramer() {}

    /** Solves an eye pose for the supplied vertical field of view and viewport aspect ratio. */
    public static Solution solve(BenchCameraFraming framing, double verticalFovDegrees, double aspectRatio) {
        if (!Double.isFinite(verticalFovDegrees) || verticalFovDegrees <= 1.0 || verticalFovDegrees >= 179.0) {
            throw new IllegalArgumentException("verticalFovDegrees must be between 1 and 179");
        }
        if (!Double.isFinite(aspectRatio) || aspectRatio <= 0.0) {
            throw new IllegalArgumentException("aspectRatio must be positive and finite");
        }
        BenchBounds3 bounds = framing.bounds();
        double fx = framing.directionX();
        double fy = framing.directionY();
        double fz = framing.directionZ();

        // A deterministic fallback avoids an unstable cross product for near-vertical views.
        double upX = 0.0;
        double upY = Math.abs(fy) > 0.999 ? 0.0 : 1.0;
        double upZ = Math.abs(fy) > 0.999 ? 1.0 : 0.0;
        double rx = fy * upZ - fz * upY;
        double ry = fz * upX - fx * upZ;
        double rz = fx * upY - fy * upX;
        double rightLength = Math.sqrt(rx * rx + ry * ry + rz * rz);
        rx /= rightLength;
        ry /= rightLength;
        rz /= rightLength;
        double ux = ry * fz - rz * fy;
        double uy = rz * fx - rx * fz;
        double uz = rx * fy - ry * fx;

        double verticalTangent = Math.tan(Math.toRadians(verticalFovDegrees) * 0.5);
        double horizontalTangent = verticalTangent * aspectRatio;
        double distance = 0.0;
        for (int corner = 0; corner < 8; corner++) {
            double qx = (((corner & 1) == 0 ? bounds.minX() : bounds.maxX()) - bounds.centerX());
            double qy = (((corner & 2) == 0 ? bounds.minY() : bounds.maxY()) - bounds.centerY());
            double qz = (((corner & 4) == 0 ? bounds.minZ() : bounds.maxZ()) - bounds.centerZ());
            double forwardOffset = qx * fx + qy * fy + qz * fz;
            double horizontalOffset = Math.abs(qx * rx + qy * ry + qz * rz);
            double verticalOffset = Math.abs(qx * ux + qy * uy + qz * uz);
            distance = Math.max(distance,
                    horizontalOffset / (framing.frameFill() * horizontalTangent) - forwardOffset);
            distance = Math.max(distance,
                    verticalOffset / (framing.frameFill() * verticalTangent) - forwardOffset);
            distance = Math.max(distance, NEAR_PADDING - forwardOffset);
        }
        double eyeX = bounds.centerX() - distance * fx;
        double eyeY = bounds.centerY() - distance * fy;
        double eyeZ = bounds.centerZ() - distance * fz;
        float yaw = (float) (Math.toDegrees(Math.atan2(fz, fx)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(fy, Math.sqrt(fx * fx + fz * fz)));
        return new Solution(eyeX, eyeY, eyeZ, bounds.centerX(), bounds.centerY(), bounds.centerZ(),
                yaw, pitch, distance);
    }

    /** Solved eye position, look target, rotation, and eye-to-target distance. */
    public record Solution(
            double eyeX, double eyeY, double eyeZ,
            double targetX, double targetY, double targetZ,
            float yaw, float pitch, double distance) {}
}