package com.zhongbai233.bench.api.neoforge.server;

import com.zhongbai233.bench.api.neoforge.BenchNeoForgeProvider;

/**
 * Server-capable extension discovered through the regular BenchProvider service.
 * The Core registrar remains available for side-neutral scenarios.
 */
public interface BenchServerProvider extends BenchNeoForgeProvider {
    void registerServer(BenchServerRegistrar registrar);
}