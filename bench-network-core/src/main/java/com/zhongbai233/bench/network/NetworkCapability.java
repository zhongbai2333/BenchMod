package com.zhongbai233.bench.network;

/** Atomic feature a network impairment backend can guarantee. */
public enum NetworkCapability {
    FIXED_LATENCY,
    DETERMINISTIC_JITTER,
    BANDWIDTH_LIMIT,
    FORWARDING_PAUSE,
    BLACKHOLE,
    HALF_CLOSE,
    CONNECTION_ABORT,
    CONNECT_REFUSE,
    MESSAGE_LOSS,
    MESSAGE_REORDER,
    MESSAGE_DUPLICATE,
    PACKET_LOSS,
    PACKET_REORDER,
    PACKET_DUPLICATE
}