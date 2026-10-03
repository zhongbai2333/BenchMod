package com.zhongbai233.bench.api.neoforge.client;

import com.zhongbai233.bench.api.ScenarioDescriptor;

public interface BenchClientRegistrar {
    void register(ScenarioDescriptor descriptor, BenchClientScenarioFactory factory);
}