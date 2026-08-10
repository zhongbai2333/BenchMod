package com.zhongbai233.bench.api;

/** Ordered phases owned by the runtime execution engine. */
public enum BenchPhase {
    DISCOVER,
    VALIDATE,
    WAIT_RUNTIME_READY,
    SETUP,
    STABILIZE,
    WARMUP,
    MEASURE,
    VERIFY,
    TEARDOWN,
    FINALIZE,
    SHUTDOWN_OR_KEEP_OPEN
}