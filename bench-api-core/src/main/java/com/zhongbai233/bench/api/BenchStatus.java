package com.zhongbai233.bench.api;

/** The terminal status of a benchmark scenario. */
public enum BenchStatus {
    PASSED,
    FAILED,
    SKIPPED,
    INCOMPATIBLE,
    TIMED_OUT,
    ABORTED,
    INCONCLUSIVE
}