package com.zhongbai233.bench.api.neoforge.server;

/** Abstract server-thread scheduler; implementations are supplied by the NeoForge adapter. */
public interface BenchScheduler {
    void execute(Runnable action);

    long currentTick();
}