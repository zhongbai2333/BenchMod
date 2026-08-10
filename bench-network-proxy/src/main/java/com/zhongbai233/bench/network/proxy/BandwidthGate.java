package com.zhongbai233.bench.network.proxy;

/** Single-writer token bucket expressed entirely in monotonic nanoseconds and payload bytes. */
final class BandwidthGate {
    private final double bytesPerNano;
    private final long burstBytes;
    private double tokens;
    private long updatedNanos;

    BandwidthGate(long bitsPerSecond, long burstBytes, long nowNanos) {
        bytesPerNano = bitsPerSecond == 0 ? 0.0 : bitsPerSecond / 8.0 / 1_000_000_000.0;
        this.burstBytes = burstBytes;
        tokens = burstBytes;
        updatedNanos = nowNanos;
    }

    long reserve(int bytes, long notBeforeNanos) {
        if (bytesPerNano == 0.0) return notBeforeNanos;
        refill(notBeforeNanos);
        if (tokens >= bytes) {
            tokens -= bytes;
            return notBeforeNanos;
        }
        double missing = bytes - tokens;
        long waitNanos = (long) Math.ceil(missing / bytesPerNano);
        tokens = 0;
        updatedNanos = Math.addExact(Math.max(notBeforeNanos, updatedNanos), waitNanos);
        return updatedNanos;
    }

    private void refill(long nowNanos) {
        if (nowNanos <= updatedNanos) return;
        tokens = Math.min(burstBytes, tokens + (nowNanos - updatedNanos) * bytesPerNano);
        updatedNanos = nowNanos;
    }
}