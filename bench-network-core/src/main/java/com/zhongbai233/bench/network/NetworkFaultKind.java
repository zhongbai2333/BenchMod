package com.zhongbai233.bench.network;

/** Scheduled or probabilistic impairment applied by a capable backend. */
public enum NetworkFaultKind {
    FORWARDING_PAUSE,
    BLACKHOLE,
    HALF_CLOSE,
    CONNECTION_ABORT,
    CONNECT_REFUSE,
    LOSS,
    REORDER,
    DUPLICATE
}