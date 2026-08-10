package com.zhongbai233.bench.network.proxy;

/** Stateless counter-based jitter; socket read boundaries do not consume mutable random state. */
final class DeterministicJitter {
    private DeterministicJitter() {}

    static long millis(long seed, long quantumSequence, long maximumMillis) {
        if (maximumMillis == 0) return 0;
        long mixed = mix64(seed ^ mix64(quantumSequence));
        long range = Math.addExact(Math.multiplyExact(maximumMillis, 2), 1);
        return Long.remainderUnsigned(mixed, range) - maximumMillis;
    }

    static boolean selected(long seed, double probability) {
        if (probability >= 1.0) return true;
        long bits = mix64(seed) >>> 11;
        return bits * 0x1.0p-53 < probability;
    }

    private static long mix64(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }
}