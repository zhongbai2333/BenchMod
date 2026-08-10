package com.zhongbai233.bench.api;

@FunctionalInterface
public interface BenchScenarioFactory {
    BenchScenario create() throws Exception;
}