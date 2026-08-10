package com.zhongbai233.bench.network;

import java.util.Objects;

/** One deterministic profile event relative to the selected activation point. */
public record NetworkFault(
        String id,
        NetworkDirection direction,
        NetworkFaultKind kind,
        NetworkSemanticLayer semanticLayer,
        long startOffsetMillis,
        long durationMillis,
        double probability) {
    public NetworkFault {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Fault id must not be blank");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(semanticLayer, "semanticLayer");
        if (startOffsetMillis < 0 || durationMillis < 0) {
            throw new IllegalArgumentException("Fault timing must not be negative");
        }
        if (!Double.isFinite(probability) || probability < 0.0 || probability > 1.0) {
            throw new IllegalArgumentException("Fault probability must be between zero and one");
        }
        if ((kind == NetworkFaultKind.FORWARDING_PAUSE || kind == NetworkFaultKind.BLACKHOLE)
                && durationMillis < 1) {
            throw new IllegalArgumentException(kind + " requires a positive duration");
        }
        if ((kind == NetworkFaultKind.LOSS || kind == NetworkFaultKind.REORDER
                || kind == NetworkFaultKind.DUPLICATE) && probability <= 0.0) {
            throw new IllegalArgumentException(kind + " requires a positive probability");
        }
        if (semanticLayer == NetworkSemanticLayer.TCP_STREAM
                && (kind == NetworkFaultKind.LOSS || kind == NetworkFaultKind.REORDER
                || kind == NetworkFaultKind.DUPLICATE)) {
            throw new IllegalArgumentException(
                    "TCP stream backends cannot model loss, reorder, or duplicate without corrupting the stream");
        }
        if (semanticLayer != NetworkSemanticLayer.TCP_STREAM
                && kind != NetworkFaultKind.LOSS && kind != NetworkFaultKind.REORDER
                && kind != NetworkFaultKind.DUPLICATE) {
            throw new IllegalArgumentException(kind + " is a TCP stream connection or forwarding fault");
        }
    }

    NetworkCapability requiredCapability() {
        return switch (kind) {
            case FORWARDING_PAUSE -> NetworkCapability.FORWARDING_PAUSE;
            case BLACKHOLE -> NetworkCapability.BLACKHOLE;
            case HALF_CLOSE -> NetworkCapability.HALF_CLOSE;
            case CONNECTION_ABORT -> NetworkCapability.CONNECTION_ABORT;
            case CONNECT_REFUSE -> NetworkCapability.CONNECT_REFUSE;
            case LOSS -> semanticLayer == NetworkSemanticLayer.IP_PACKET
                    ? NetworkCapability.PACKET_LOSS : NetworkCapability.MESSAGE_LOSS;
            case REORDER -> semanticLayer == NetworkSemanticLayer.IP_PACKET
                    ? NetworkCapability.PACKET_REORDER : NetworkCapability.MESSAGE_REORDER;
            case DUPLICATE -> semanticLayer == NetworkSemanticLayer.IP_PACKET
                    ? NetworkCapability.PACKET_DUPLICATE : NetworkCapability.MESSAGE_DUPLICATE;
        };
    }
}