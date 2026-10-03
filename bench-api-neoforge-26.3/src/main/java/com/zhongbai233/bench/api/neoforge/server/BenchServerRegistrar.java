package com.zhongbai233.bench.api.neoforge.server;

import com.zhongbai233.bench.api.ScenarioDescriptor;

public interface BenchServerRegistrar {
    void register(ScenarioDescriptor descriptor, BenchServerScenarioFactory factory);
}