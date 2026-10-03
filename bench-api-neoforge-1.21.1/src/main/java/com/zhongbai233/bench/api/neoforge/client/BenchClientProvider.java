package com.zhongbai233.bench.api.neoforge.client;

import com.zhongbai233.bench.api.neoforge.BenchNeoForgeProvider;

/** Client-capable Provider discovered through the regular BenchProvider service. */
public interface BenchClientProvider extends BenchNeoForgeProvider {
    void registerClient(BenchClientRegistrar registrar);
}