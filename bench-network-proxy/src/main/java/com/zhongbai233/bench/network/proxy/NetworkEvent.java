package com.zhongbai233.bench.network.proxy;

import com.zhongbai233.bench.network.NetworkDirection;

/** One auditable backend event measured relative to proxy activation. */
public record NetworkEvent(
        long monotonicOffsetNanos,
        long connectionId,
        NetworkDirection direction,
        String type,
        long streamOffset,
        long queueBytes,
        String detail) {}