package com.zhongbai233.bench.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class ScenarioTimeoutsTest {
    @Test
    void declaredTimeoutRaisesTheGlobalBudget() {
        assertEquals(1800, ScenarioTimeouts.effectivePhaseTicks(1200, Duration.ofSeconds(90)));
    }

    @Test
    void declaredTimeoutNeverLowersTheGlobalBudget() {
        assertEquals(1200, ScenarioTimeouts.effectivePhaseTicks(1200, Duration.ofSeconds(10)));
    }

    @Test
    void hugeDurationsSaturateInsteadOfOverflowing() {
        assertEquals(Long.MAX_VALUE,
                ScenarioTimeouts.effectivePhaseTicks(1200, Duration.ofSeconds(Long.MAX_VALUE / 2000)));
    }
}
