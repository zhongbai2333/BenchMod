package com.zhongbai233.bench.api;

/** ServiceLoader entry point implemented by test-only benchmark providers. */
public interface BenchProvider {
    String id();

    BenchCompatibility compatibility();

    void register(BenchRegistrar registrar);
}