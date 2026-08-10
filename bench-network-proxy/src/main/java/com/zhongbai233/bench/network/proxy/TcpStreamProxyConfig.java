package com.zhongbai233.bench.network.proxy;

import com.zhongbai233.bench.network.NetworkProfile;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Objects;

/** Immutable startup configuration for one TCP stream proxy backend session. */
public record TcpStreamProxyConfig(
        InetAddress listenAddress,
        int listenPort,
        InetSocketAddress upstreamAddress,
        NetworkProfile profile,
        String phase,
        Duration connectTimeout,
        Duration shutdownTimeout,
        NetworkEventSink eventSink) {
    public TcpStreamProxyConfig {
        Objects.requireNonNull(listenAddress, "listenAddress");
        Objects.requireNonNull(upstreamAddress, "upstreamAddress");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(connectTimeout, "connectTimeout");
        Objects.requireNonNull(shutdownTimeout, "shutdownTimeout");
        eventSink = eventSink == null ? NetworkEventSink.noop() : eventSink;
        if (listenPort < 0 || listenPort > 65535 || upstreamAddress.getPort() < 1
                || phase == null || phase.isBlank() || connectTimeout.isNegative() || connectTimeout.isZero()
                || shutdownTimeout.isNegative() || shutdownTimeout.isZero()) {
            throw new IllegalArgumentException("Invalid TCP proxy configuration");
        }
    }
}