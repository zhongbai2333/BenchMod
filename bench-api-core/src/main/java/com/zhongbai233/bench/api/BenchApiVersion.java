package com.zhongbai233.bench.api;

/** Single source of truth for the Core Provider SPI version. */
public final class BenchApiVersion {
    public static final int MAJOR = 0;
    public static final int MINOR = 1;

    private BenchApiVersion() {}

    public static BenchCompatibility currentCompatibility() {
        return BenchCompatibility.exactly(MAJOR, MINOR);
    }
}