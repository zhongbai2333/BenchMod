package com.zhongbai233.bench.api.neoforge.client;

/** Non-blocking integrated-client scenario, advanced once per client tick. */
public interface BenchClientScenario {
    default void setup(BenchClientContext context) throws Exception {}

    default BenchClientStepResult stabilize(BenchClientContext context) throws Exception {
        return BenchClientStepResult.COMPLETE;
    }

    default BenchClientStepResult warmup(BenchClientContext context) throws Exception {
        return BenchClientStepResult.COMPLETE;
    }

    default BenchClientStepResult measure(BenchClientContext context) throws Exception {
        return BenchClientStepResult.COMPLETE;
    }

    default void verify(BenchClientContext context) throws Exception {}

    default void teardown(BenchClientContext context) throws Exception {}
}