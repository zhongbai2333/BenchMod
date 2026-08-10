package com.zhongbai233.bench.network.proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DeterministicJitterTest {
    @Test
    void trajectoryIsRepeatableAndBoundedByQuantumSequence() {
        long[] first = new long[1_000];
        boolean varies = false;
        for (int index = 0; index < first.length; index++) {
            first[index] = DeterministicJitter.millis(123456789L, index, 25);
            assertTrue(first[index] >= -25 && first[index] <= 25);
            if (index > 0 && first[index] != first[0]) varies = true;
        }
        assertTrue(varies);
        for (int index = 0; index < first.length; index++) {
            assertEquals(first[index], DeterministicJitter.millis(123456789L, index, 25));
        }
        assertNotEquals(trajectoryHash(123456789L), trajectoryHash(987654321L));
    }

    private static long trajectoryHash(long seed) {
        long hash = 1;
        for (int index = 0; index < 100; index++) {
            hash = 31 * hash + DeterministicJitter.millis(seed, index, 25);
        }
        return hash;
    }
}