package com.zhongbai233.bench.network;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/** Immutable capability advertisement used before starting an experiment. */
public record NetworkBackendCapabilities(String backendId, Set<NetworkCapability> capabilities) {
    public NetworkBackendCapabilities {
        if (backendId == null || backendId.isBlank()) throw new IllegalArgumentException("backendId must not be blank");
        Objects.requireNonNull(capabilities, "capabilities");
        capabilities = Set.copyOf(capabilities);
    }

    public static NetworkBackendCapabilities tcpStreamProxy() {
        return new NetworkBackendCapabilities("tcp-stream-proxy", EnumSet.of(
                NetworkCapability.FIXED_LATENCY,
                NetworkCapability.DETERMINISTIC_JITTER,
                NetworkCapability.BANDWIDTH_LIMIT,
                NetworkCapability.FORWARDING_PAUSE,
                NetworkCapability.CONNECTION_ABORT));
    }
}