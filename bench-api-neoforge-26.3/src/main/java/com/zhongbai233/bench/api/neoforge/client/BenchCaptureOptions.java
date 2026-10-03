package com.zhongbai233.bench.api.neoforge.client;

/**
 * Capture policy for a single screenshot.
 *
 * @param hideHud            hides the in-game HUD for the captured frame only
 * @param requireRenderReady defers the capture until chunk meshes and resources have settled
 * @param stableFrames       consecutive comparable frame intervals required before capturing
 */
public record BenchCaptureOptions(boolean hideHud, boolean requireRenderReady, int stableFrames) {
    private static final BenchCaptureOptions DEFAULT = new BenchCaptureOptions(true, true, 8);
    private static final BenchCaptureOptions IMMEDIATE = new BenchCaptureOptions(false, false, 0);

    public BenchCaptureOptions {
        if (stableFrames < 0) throw new IllegalArgumentException("stableFrames must not be negative");
    }

    /** Gated capture: waits for a ready render pipeline and settled frame pacing. */
    public static BenchCaptureOptions defaults() {
        return DEFAULT;
    }

    /** Ungated capture of the next completed frame, HUD included. */
    public static BenchCaptureOptions immediate() {
        return IMMEDIATE;
    }

    public BenchCaptureOptions withHiddenHud(boolean hidden) {
        return new BenchCaptureOptions(hidden, requireRenderReady, stableFrames);
    }

    public BenchCaptureOptions withRenderReady(boolean required) {
        return new BenchCaptureOptions(hideHud, required, stableFrames);
    }

    public BenchCaptureOptions withStableFrames(int frames) {
        return new BenchCaptureOptions(hideHud, requireRenderReady, frames);
    }
}
