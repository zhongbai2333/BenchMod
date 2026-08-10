package com.zhongbai233.bench.api.client.gui;

/** Policy for selector-driven widget-region capture. */
public record BenchGuiCaptureOptions(int padding, int stableFrames, boolean failOnClippedBounds) {
    private static final BenchGuiCaptureOptions DEFAULT = new BenchGuiCaptureOptions(8, 4, false);

    public BenchGuiCaptureOptions {
        if (padding < 0 || stableFrames < 0) {
            throw new IllegalArgumentException("GUI capture padding and stableFrames must not be negative");
        }
    }

    public static BenchGuiCaptureOptions defaults() { return DEFAULT; }

    public BenchGuiCaptureOptions withPadding(int value) {
        return new BenchGuiCaptureOptions(value, stableFrames, failOnClippedBounds);
    }

    public BenchGuiCaptureOptions withStableFrames(int value) {
        return new BenchGuiCaptureOptions(padding, value, failOnClippedBounds);
    }

    public BenchGuiCaptureOptions failingOnClippedBounds() {
        return new BenchGuiCaptureOptions(padding, stableFrames, true);
    }
}