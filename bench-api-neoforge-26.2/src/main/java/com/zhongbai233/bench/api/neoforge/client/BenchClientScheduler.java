package com.zhongbai233.bench.api.neoforge.client;

/** Client-main-thread scheduler supplied by the Runtime. */
public interface BenchClientScheduler {
    void execute(Runnable action);

    long currentTick();
}