package com.zhongbai233.bench.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class NeoForgeLineTest {
    @Test
    void selectsTheExactMinecraftLineIncludingPrereleases() {
        assertEquals("26.1", NeoForgeLine.fromVersion("26.1.2.76"));
        assertEquals("26.3", NeoForgeLine.fromVersion("26.3.0.45-beta"));
    }

    @Test
    void neverFallsBackToAnOlderAdapter() {
        assertThrows(IllegalArgumentException.class, () -> NeoForgeLine.fromVersion("26.2.0.1-beta"));
        assertThrows(IllegalArgumentException.class, () -> NeoForgeLine.fromVersion("27.1.0.1"));
        assertThrows(IllegalArgumentException.class, () -> NeoForgeLine.fromVersion("unknown"));
        assertThrows(IllegalArgumentException.class, () -> NeoForgeLine.requireSupported(""));
    }

    @Test
    void rejectsAMismatchedExplicitSelection() {
        NeoForgeLine.requireMatching("26.3", "26.3.0.45-beta");
        assertThrows(IllegalArgumentException.class,
                () -> NeoForgeLine.requireMatching("26.1", "26.3.0.45-beta"));
    }
}
