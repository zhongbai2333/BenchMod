package com.zhongbai233.bench.api;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BenchCompatibilityTest {
    @Test
    void supportsOnlyDeclaredMajorAndMinorRange() {
        var compatibility = new BenchCompatibility(1, 2, 4);

        assertTrue(compatibility.supports(1, 2));
        assertTrue(compatibility.supports(1, 4));
        assertFalse(compatibility.supports(1, 5));
        assertFalse(compatibility.supports(2, 2));
    }

    @Test
    void rejectsInvertedRange() {
        assertThrows(IllegalArgumentException.class, () -> new BenchCompatibility(1, 3, 2));
    }
}