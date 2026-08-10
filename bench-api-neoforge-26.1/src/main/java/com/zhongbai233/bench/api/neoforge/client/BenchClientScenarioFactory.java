package com.zhongbai233.bench.api.neoforge.client;

@FunctionalInterface
public interface BenchClientScenarioFactory {
    BenchClientScenario create(BenchClientContext context) throws Exception;
}