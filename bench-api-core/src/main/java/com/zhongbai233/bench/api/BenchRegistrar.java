package com.zhongbai233.bench.api;

public interface BenchRegistrar {
    void register(ScenarioDescriptor descriptor, BenchScenarioFactory factory);
}