package com.zhongbai233.bench.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zhongbai233.bench.api.BenchStatus;
import com.zhongbai233.bench.api.neoforge.server.BenchCancellationToken;
import com.zhongbai233.bench.api.neoforge.server.BenchMetricRecorder;
import com.zhongbai233.bench.api.neoforge.server.BenchScheduler;
import com.zhongbai233.bench.api.neoforge.server.BenchServerContext;
import com.zhongbai233.bench.api.neoforge.server.BenchServerScenario;
import com.zhongbai233.bench.api.neoforge.server.BenchStepResult;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ServerScenarioRunnerTest {
    @Test
    void advancesAcrossTicksAndTearsDown() {
        Fixture fixture = new Fixture();
        List<String> calls = new ArrayList<>();
        int[] stabilizeTicks = {0};
        var scenario = new BenchServerScenario() {
            @Override public void setup(BenchServerContext context) { calls.add("setup"); }
            @Override public BenchStepResult stabilize(BenchServerContext context) {
                calls.add("stabilize");
                return ++stabilizeTicks[0] == 2 ? BenchStepResult.COMPLETE : BenchStepResult.CONTINUE;
            }
            @Override public BenchStepResult warmup(BenchServerContext context) {
                calls.add("warmup");
                return BenchStepResult.COMPLETE;
            }
            @Override public BenchStepResult measure(BenchServerContext context) {
                calls.add("measure");
                return BenchStepResult.COMPLETE;
            }
            @Override public void verify(BenchServerContext context) { calls.add("verify"); }
            @Override public void teardown(BenchServerContext context) { calls.add("teardown"); }
        };
        ServerScenarioRunner runner = fixture.runner(scenario, 10);

        runner.start();
        fixture.tick(runner, 4);

        assertEquals(BenchStatus.PASSED, runner.status());
        assertNull(runner.failure());
        assertEquals(List.of("setup", "stabilize", "stabilize", "warmup", "measure", "verify", "teardown"), calls);
    }

    @Test
    void timesOutAndTearsDownExactlyOnce() {
        Fixture fixture = new Fixture();
        int[] teardowns = {0};
        var scenario = new BenchServerScenario() {
            @Override public BenchStepResult stabilize(BenchServerContext context) { return BenchStepResult.CONTINUE; }
            @Override public void teardown(BenchServerContext context) { teardowns[0]++; }
        };
        ServerScenarioRunner runner = fixture.runner(scenario, 2);

        runner.start();
        fixture.tick(runner, 3);
        runner.tick();

        assertEquals(BenchStatus.TIMED_OUT, runner.status());
        assertEquals(1, teardowns[0]);
    }

    @Test
    void cancellationProducesAbortedStatusAndTearsDownExactlyOnce() {
        Fixture fixture = new Fixture();
        int[] teardowns = {0};
        ServerScenarioRunner runner = fixture.runner(new BenchServerScenario() {
            @Override public void teardown(BenchServerContext context) { teardowns[0]++; }
        }, 10);

        runner.start();
        fixture.cancelled = true;
        fixture.tick(runner, 1);
        runner.tick();

        assertEquals(BenchStatus.ABORTED, runner.status());
        assertEquals(1, teardowns[0]);
        assertTrue(runner.failure().contains("test cancellation"));
    }

    @Test
    void correctnessAssertionBecomesScenarioFailure() {
        Fixture fixture = new Fixture();
        var scenario = new BenchServerScenario() {
            @Override public BenchStepResult stabilize(BenchServerContext context) { return BenchStepResult.COMPLETE; }
            @Override public BenchStepResult warmup(BenchServerContext context) { return BenchStepResult.COMPLETE; }
            @Override public BenchStepResult measure(BenchServerContext context) { return BenchStepResult.COMPLETE; }
            @Override public void verify(BenchServerContext context) { throw new AssertionError("wrong workload"); }
        };
        ServerScenarioRunner runner = fixture.runner(scenario, 10);

        runner.start();
        fixture.tick(runner, 3);

        assertEquals(BenchStatus.FAILED, runner.status());
        assertTrue(runner.failure().contains("wrong workload"));
    }

    private static final class Fixture implements BenchScheduler, BenchCancellationToken {
        private long tick;
        private boolean cancelled;
        private final BenchMetricRecorder metrics = new BenchMetricRecorder() {
            @Override public void record(com.zhongbai233.bench.api.BenchMetricDescriptor descriptor, long value) {}
            @Override public void record(com.zhongbai233.bench.api.BenchMetricDescriptor descriptor, double value) {}
        };

        ServerScenarioRunner runner(BenchServerScenario scenario, long timeout) {
            return new ServerScenarioRunner(
                    context(),
                    scenario,
                    timeout);
        }

        private BenchServerContext context() {
            return (BenchServerContext) Proxy.newProxyInstance(
                    BenchServerContext.class.getClassLoader(),
                    new Class<?>[] {BenchServerContext.class},
                    (proxy, method, arguments) -> {
                        Class<?> returnType = method.getReturnType();
                        if (returnType.isInstance(this)) {
                            return this;
                        }
                        if (returnType.isInstance(metrics)) {
                            return metrics;
                        }
                        if (returnType == Path.class) {
                            return Path.of("build/results");
                        }
                        if (returnType == long.class || returnType == Long.class) {
                            return 1L;
                        }
                        return null;
                    });
        }

        void tick(ServerScenarioRunner runner, int count) {
            for (int i = 0; i < count; i++) {
                tick++;
                runner.tick();
            }
        }

        @Override public void execute(Runnable action) { action.run(); }
        @Override public long currentTick() { return tick; }
        @Override public boolean isCancelled() { return cancelled; }
        @Override public String reason() { return "test cancellation"; }
    }
}