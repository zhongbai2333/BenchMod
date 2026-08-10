package com.zhongbai233.bench.api.neoforge.client;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** A camera path bound to the running client, advanced once per client tick by the scenario. */
public interface BenchCameraPlayback {
    /** Applies the pose for the next client tick. Returns {@code true} once playback is finished. */
    boolean advance();

    /** Returns {@code true} after a {@code ONCE} path reached its last keyframe or {@link #stop()} ran. */
    boolean isFinished();

    /** Ticks advanced so far, independent of the loop mode. */
    long elapsedTicks();

    /** The pose applied by the most recent {@link #advance()}. */
    BenchClientPose currentPose();

    /** Screenshot futures created by keyframe captures, in trigger order. */
    List<CompletableFuture<Path>> captures();

    /** Returns {@code true} when every keyframe capture has finished writing. */
    boolean capturesComplete();

    /** Stops playback; further {@link #advance()} calls do nothing and report finished. */
    void stop();
}
