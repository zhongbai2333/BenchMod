package com.zhongbai233.bench.api.neoforge.server;

/** Non-blocking server scenario contract. Each step is called once per server tick. */
public interface BenchServerScenario {
    default void setup(BenchServerContext context) throws Exception {}

    default BenchStepResult stabilize(BenchServerContext context) throws Exception {
        return BenchStepResult.COMPLETE;
    }

    default BenchStepResult warmup(BenchServerContext context) throws Exception {
        return BenchStepResult.COMPLETE;
    }

    default BenchStepResult measure(BenchServerContext context) throws Exception {
        return BenchStepResult.COMPLETE;
    }

    default void verify(BenchServerContext context) throws Exception {}

    default void teardown(BenchServerContext context) throws Exception {}
}