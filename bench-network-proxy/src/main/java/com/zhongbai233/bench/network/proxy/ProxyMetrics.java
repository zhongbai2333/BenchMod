package com.zhongbai233.bench.network.proxy;

import java.util.concurrent.atomic.AtomicLong;

final class ProxyMetrics {
    final AtomicLong accepted = new AtomicLong();
    final AtomicLong active = new AtomicLong();
    final AtomicLong completed = new AtomicLong();
    final AtomicLong aborted = new AtomicLong();
    final AtomicLong c2sRead = new AtomicLong();
    final AtomicLong c2sForwarded = new AtomicLong();
    final AtomicLong s2cRead = new AtomicLong();
    final AtomicLong s2cForwarded = new AtomicLong();
    final AtomicLong queued = new AtomicLong();
    final AtomicLong peakQueued = new AtomicLong();
    final AtomicLong quanta = new AtomicLong();
    final AtomicLong failures = new AtomicLong();

    void queue(long bytes) {
        long current = queued.addAndGet(bytes);
        peakQueued.accumulateAndGet(current, Math::max);
    }

    void dequeue(long bytes) {
        queued.addAndGet(-bytes);
    }

    ProxyMetricsSnapshot snapshot() {
        return new ProxyMetricsSnapshot(accepted.get(), active.get(), completed.get(), aborted.get(),
                c2sRead.get(), c2sForwarded.get(), s2cRead.get(), s2cForwarded.get(), queued.get(),
                peakQueued.get(), quanta.get(), failures.get());
    }
}