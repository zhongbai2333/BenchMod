package com.zhongbai233.bench.api;

/**
 * Provider-owned workload hooks. A runtime invokes these hooks through its own
 * side-aware scheduler; scenarios must not create threads.
 */
public interface BenchScenario {
    default void setup() throws Exception {}

    default void stabilize() throws Exception {}

    default void warmup() throws Exception {}

    default void measure() throws Exception {}

    default void verify() throws Exception {}

    default void teardown() throws Exception {}
}