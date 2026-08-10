package com.zhongbai233.bench.network.proxy;

/** Thread-safe destination for proxy lifecycle and shaping events. */
@FunctionalInterface
public interface NetworkEventSink extends AutoCloseable {
    void record(NetworkEvent event);

    @Override
    default void close() {}

    static NetworkEventSink noop() {
        return event -> {};
    }
}