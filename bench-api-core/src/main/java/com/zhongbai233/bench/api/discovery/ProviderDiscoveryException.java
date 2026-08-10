package com.zhongbai233.bench.api.discovery;

public final class ProviderDiscoveryException extends RuntimeException {
    public ProviderDiscoveryException(String message) {
        super(message);
    }

    public ProviderDiscoveryException(String message, Throwable cause) {
        super(message, cause);
    }
}