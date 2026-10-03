package com.zhongbai233.bench.api.neoforge;

import com.zhongbai233.bench.api.BenchProvider;
import com.zhongbai233.bench.api.BenchRegistrar;

/** Shared base for side-specific NeoForge Providers. */
public interface BenchNeoForgeProvider extends BenchProvider {
    @Override
    default void register(BenchRegistrar registrar) {}
}