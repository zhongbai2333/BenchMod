package com.zhongbai233.bench.api.neoforge.client;

/** How a camera path behaves once its last keyframe is reached. */
public enum BenchCameraPathMode {
    /** Stops on the last keyframe and reports the playback as finished. */
    ONCE,
    /** Restarts from the first keyframe and never finishes on its own. */
    LOOP,
    /** Plays the timeline backwards to the first keyframe, then forwards again. */
    PING_PONG
}
