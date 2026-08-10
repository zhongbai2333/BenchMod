package com.zhongbai233.bench.network.proxy;

/** Immutable aggregate counters suitable for paired report serialization. */
public record ProxyMetricsSnapshot(
        long acceptedConnections,
        long activeConnections,
        long completedConnections,
        long abortedConnections,
        long clientToServerBytesRead,
        long clientToServerBytesForwarded,
        long serverToClientBytesRead,
        long serverToClientBytesForwarded,
        long queuedBytes,
        long peakQueuedBytes,
        long forwardedQuanta,
        long failures) {}