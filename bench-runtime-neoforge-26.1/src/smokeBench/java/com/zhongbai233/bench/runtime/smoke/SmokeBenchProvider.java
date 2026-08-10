package com.zhongbai233.bench.runtime.smoke;

import com.zhongbai233.bench.api.BenchApiVersion;
import com.zhongbai233.bench.api.BenchCompatibility;
import com.zhongbai233.bench.api.ScenarioDescriptor;
import com.zhongbai233.bench.api.neoforge.server.BenchServerContext;
import com.zhongbai233.bench.api.neoforge.server.BenchServerProvider;
import com.zhongbai233.bench.api.neoforge.server.BenchServerRegistrar;
import com.zhongbai233.bench.api.neoforge.server.BenchServerScenario;
import com.zhongbai233.bench.api.neoforge.server.BenchStepResult;
import java.time.Duration;
import java.util.Set;

public final class SmokeBenchProvider implements BenchServerProvider {
    public SmokeBenchProvider() {}

    @Override public String id() { return "modbench-smoke"; }

    @Override public BenchCompatibility compatibility() {
        return BenchApiVersion.currentCompatibility();
    }

    @Override
    public void registerServer(BenchServerRegistrar registrar) {
        registrar.register(
                new ScenarioDescriptor("modbench.server-smoke", "ModBench Server Smoke", Set.of("smoke"), Duration.ofSeconds(10)),
                context -> new SmokeScenario());
    }

    private static final class SmokeScenario implements BenchServerScenario {
        private int stabilizeTicks;
        private int warmupTicks;
        private int measuredTicks;

        @Override
        public void setup(BenchServerContext context) {
            if (!context.server().isRunning() || context.level() != context.server().overworld()) {
                throw new IllegalStateException("Dedicated server context is not ready");
            }
        }

        @Override
        public BenchStepResult stabilize(BenchServerContext context) {
            return ++stabilizeTicks >= 2 ? BenchStepResult.COMPLETE : BenchStepResult.CONTINUE;
        }

        @Override
        public BenchStepResult warmup(BenchServerContext context) {
            return ++warmupTicks >= 3 ? BenchStepResult.COMPLETE : BenchStepResult.CONTINUE;
        }

        @Override
        public BenchStepResult measure(BenchServerContext context) {
            return ++measuredTicks >= 5 ? BenchStepResult.COMPLETE : BenchStepResult.CONTINUE;
        }

        @Override
        public void verify(BenchServerContext context) {
            if (stabilizeTicks != 2 || warmupTicks != 3 || measuredTicks != 5) {
                throw new AssertionError("Unexpected workload counts");
            }
        }
    }
}