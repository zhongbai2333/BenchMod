package com.zhongbai233.bench.network;

/** Continuous stream shaping applied to one traffic direction. Zero values disable a feature. */
public record NetworkDirectionProfile(
        long baseLatencyMillis,
        long jitterMillis,
        long bandwidthBitsPerSecond,
        long burstBytes,
    long maxQueueBytes,
    int streamQuantumBytes) {
    public static final int DEFAULT_STREAM_QUANTUM_BYTES = 16 * 1024;
    public static final NetworkDirectionProfile PASSTHROUGH =
        new NetworkDirectionProfile(0, 0, 0, 0, 1024 * 1024, DEFAULT_STREAM_QUANTUM_BYTES);

    public NetworkDirectionProfile {
        if (baseLatencyMillis < 0 || jitterMillis < 0 || bandwidthBitsPerSecond < 0
                || burstBytes < 0 || maxQueueBytes < 1 || streamQuantumBytes < 1) {
            throw new IllegalArgumentException("Network shaping values must not be negative");
        }
        if (bandwidthBitsPerSecond == 0 && burstBytes != 0) {
            throw new IllegalArgumentException("burstBytes requires a bandwidth limit");
        }
        if (bandwidthBitsPerSecond > 0 && (burstBytes < 1 || maxQueueBytes < burstBytes)) {
            throw new IllegalArgumentException("Bandwidth limiting requires a positive burst and a bounded queue");
        }
        if (streamQuantumBytes > maxQueueBytes) {
            throw new IllegalArgumentException("streamQuantumBytes must not exceed maxQueueBytes");
        }
        if (maxQueueBytes % streamQuantumBytes != 0) {
            throw new IllegalArgumentException("maxQueueBytes must be an exact multiple of streamQuantumBytes");
        }
    }
}