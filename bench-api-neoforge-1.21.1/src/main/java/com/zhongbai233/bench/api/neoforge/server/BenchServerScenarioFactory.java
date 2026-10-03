package com.zhongbai233.bench.api.neoforge.server;

@FunctionalInterface
public interface BenchServerScenarioFactory {
    BenchServerScenario create(BenchServerContext context) throws Exception;
}