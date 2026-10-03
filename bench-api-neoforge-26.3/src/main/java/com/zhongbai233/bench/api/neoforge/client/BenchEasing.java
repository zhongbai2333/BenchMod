package com.zhongbai233.bench.api.neoforge.client;

/** Interpolation curves applied between two camera keyframes. */
public enum BenchEasing {
    LINEAR,
    EASE_IN,
    EASE_OUT,
    EASE_IN_OUT,
    SMOOTH_STEP;

    /** Maps a normalized progress onto this curve. Input outside {@code [0,1]} is clamped. */
    public double apply(double progress) {
        double t = Math.max(0.0, Math.min(1.0, progress));
        return switch (this) {
            case LINEAR -> t;
            case EASE_IN -> t * t;
            case EASE_OUT -> t * (2.0 - t);
            case EASE_IN_OUT -> t < 0.5 ? 2.0 * t * t : 1.0 - 2.0 * (1.0 - t) * (1.0 - t);
            case SMOOTH_STEP -> t * t * (3.0 - 2.0 * t);
        };
    }
}
