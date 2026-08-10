package com.zhongbai233.bench.api.client;

/** World-space axis-aligned bounds for camera framing and visibility checks. */
public record BenchBounds3(
        double minX, double minY, double minZ,
        double maxX, double maxY, double maxZ) {
    public BenchBounds3 {
        requireFinite(minX, "minX");
        requireFinite(minY, "minY");
        requireFinite(minZ, "minZ");
        requireFinite(maxX, "maxX");
        requireFinite(maxY, "maxY");
        requireFinite(maxZ, "maxZ");
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            throw new IllegalArgumentException("Bounds minimums must not exceed maximums");
        }
        if (minX == maxX && minY == maxY && minZ == maxZ) {
            throw new IllegalArgumentException("Bounds must have a non-zero extent");
        }
    }

    public double centerX() { return minX * 0.5 + maxX * 0.5; }
    public double centerY() { return minY * 0.5 + maxY * 0.5; }
    public double centerZ() { return minZ * 0.5 + maxZ * 0.5; }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException(name + " must be finite");
    }
}