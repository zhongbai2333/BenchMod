package com.zhongbai233.bench.runtime.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zhongbai233.bench.api.BenchStatus;
import com.zhongbai233.bench.api.neoforge.client.BenchClientContext;
import com.zhongbai233.bench.api.neoforge.client.BenchClientScenario;
import com.zhongbai233.bench.api.neoforge.client.BenchClientScheduler;
import com.zhongbai233.bench.api.neoforge.client.BenchClientStepResult;
import com.zhongbai233.bench.api.neoforge.server.BenchCancellationToken;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ClientScenarioRunnerTest {
    @Test
    void advancesAcrossClientTicksAndTearsDown() {
        Fixture fixture = new Fixture();
        List<String> calls = new ArrayList<>();
        ClientScenarioRunner runner = fixture.runner(new BenchClientScenario() {
            @Override public void setup(BenchClientContext context) { calls.add("setup"); }
            @Override public BenchClientStepResult stabilize(BenchClientContext context) {
                calls.add("stabilize"); return BenchClientStepResult.COMPLETE;
            }
            @Override public BenchClientStepResult warmup(BenchClientContext context) {
                calls.add("warmup"); return BenchClientStepResult.COMPLETE;
            }
            @Override public BenchClientStepResult measure(BenchClientContext context) {
                calls.add("measure"); return BenchClientStepResult.COMPLETE;
            }
            @Override public void verify(BenchClientContext context) { calls.add("verify"); }
            @Override public void teardown(BenchClientContext context) { calls.add("teardown"); }
        }, 10);

        runner.start();
        fixture.tick(runner, 3);

        assertEquals(BenchStatus.PASSED, runner.status());
        assertNull(runner.failure());
        assertEquals(List.of("setup", "stabilize", "warmup", "measure", "verify", "teardown"), calls);
        assertEquals(
                List.of("SETUP", "STABILIZE", "WARMUP", "MEASURE", "VERIFY", "TEARDOWN"),
                runner.phaseRecords().stream().map(record -> record.phase().name()).toList());
        assertTrue(runner.phaseRecords().stream().allMatch(record -> record.outcome().equals("completed")));
    }

    @Test
    void timeoutTearsDownExactlyOnce() {
        Fixture fixture = new Fixture();
        int[] teardowns = {0};
        ClientScenarioRunner runner = fixture.runner(new BenchClientScenario() {
            @Override public BenchClientStepResult stabilize(BenchClientContext context) {
                return BenchClientStepResult.CONTINUE;
            }
            @Override public void teardown(BenchClientContext context) { teardowns[0]++; }
        }, 2);

        runner.start();
        fixture.tick(runner, 3);
        runner.tick();

        assertEquals(BenchStatus.TIMED_OUT, runner.status());
        assertEquals(1, teardowns[0]);
        var stabilize = runner.phaseRecords().stream()
                .filter(record -> record.phase().name().equals("STABILIZE")).findFirst().orElseThrow();
        assertEquals("timed_out", stabilize.outcome());
    }

    private static final class Fixture implements BenchClientScheduler, BenchCancellationToken {
        private long tick;

        ClientScenarioRunner runner(BenchClientScenario scenario, long timeout) {
            BenchClientContext context = (BenchClientContext) Proxy.newProxyInstance(
                    BenchClientContext.class.getClassLoader(), new Class<?>[] {BenchClientContext.class},
                    (proxy, method, arguments) -> {
                        if (method.getReturnType().isInstance(this)) return this;
                        if (method.getReturnType() == long.class) return 1L;
                        return null;
                    });
            return new ClientScenarioRunner(context, scenario, timeout);
        }

        void tick(ClientScenarioRunner runner, int count) {
            for (int i = 0; i < count; i++) {
                tick++;
                runner.tick();
            }
        }

        @Override public void execute(Runnable action) { action.run(); }
        @Override public long currentTick() { return tick; }
        @Override public boolean isCancelled() { return false; }
        @Override public String reason() { return ""; }
    }
}