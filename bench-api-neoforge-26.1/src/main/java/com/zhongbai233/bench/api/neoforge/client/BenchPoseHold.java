package com.zhongbai233.bench.api.neoforge.client;

/** Runtime-owned stationary camera pose that remains applied until explicitly released. */
public interface BenchPoseHold extends AutoCloseable {
    BenchClientPose pose();
    boolean active();
    void release();

    @Override
    default void close() {
        release();
    }
}