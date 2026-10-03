package com.zhongbai233.example;

import com.zhongbai233.bench.api.BenchApiVersion;
import com.zhongbai233.bench.api.BenchCompatibility;
import com.zhongbai233.bench.api.neoforge.client.BenchClientProvider;
import com.zhongbai233.bench.api.neoforge.client.BenchClientRegistrar;
import com.zhongbai233.bench.api.neoforge.graphics.GraphicsMigrationSuite;

/** Copy this delegation into a target mod's src/bench Provider to opt into the reusable probes. */
public final class GraphicsMigrationProvider implements BenchClientProvider {
    public GraphicsMigrationProvider() {}
    @Override public String id() { return "graphicsmigration"; }
    @Override public BenchCompatibility compatibility() { return BenchApiVersion.currentCompatibility(); }
    @Override public void registerClient(BenchClientRegistrar registrar) { GraphicsMigrationSuite.register(registrar); }
}
