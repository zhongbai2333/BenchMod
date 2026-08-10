package com.zhongbai233.bench.api.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zhongbai233.bench.api.BenchPhase;
import com.zhongbai233.bench.api.BenchScenario;
import com.zhongbai233.bench.api.BenchStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScenarioExecutorTest {
    private final ScenarioExecutor executor = new ScenarioExecutor(
            Clock.fixed(Instant.parse("2026-07-26T00:00:00Z"), ZoneOffset.UTC));

    @Test
    void executesHappyPathInOrder() {
        List<String> calls = new ArrayList<>();
        var scenario = recordingScenario(calls, false);

        ScenarioResult result = executor.execute("test.happy", scenario);

        assertEquals(BenchStatus.PASSED, result.status());
        assertEquals(List.of("setup", "stabilize", "warmup", "measure", "verify", "teardown"), calls);
        assertEquals(List.of(BenchPhase.SETUP, BenchPhase.STABILIZE, BenchPhase.WARMUP,
                BenchPhase.MEASURE, BenchPhase.VERIFY, BenchPhase.TEARDOWN),
                result.phases().stream().map(PhaseEvent::phase).toList());
    }

    @Test
    void failureSkipsRemainingWorkButAlwaysTearsDown() {
        List<String> calls = new ArrayList<>();
        var scenario = recordingScenario(calls, true);

        ScenarioResult result = executor.execute("test.failure", scenario);

        assertEquals(BenchStatus.FAILED, result.status());
        assertEquals(List.of("setup", "stabilize", "teardown"), calls);
        assertTrue(result.failure().contains("stabilize failed"));
    }

    private static BenchScenario recordingScenario(List<String> calls, boolean failStabilize) {
        return new BenchScenario() {
            @Override public void setup() { calls.add("setup"); }
            @Override public void stabilize() {
                calls.add("stabilize");
                if (failStabilize) throw new IllegalStateException("stabilize failed");
            }
            @Override public void warmup() { calls.add("warmup"); }
            @Override public void measure() { calls.add("measure"); }
            @Override public void verify() { calls.add("verify"); }
            @Override public void teardown() { calls.add("teardown"); }
        };
    }
}