package com.zhongbai233.bench.api.neoforge.server;

/** Cooperative cancellation shared by timeout, shutdown and external abort paths. */
public interface BenchCancellationToken {
    boolean isCancelled();

    String reason();
}