package com.zhongbai233.bench.network.proxy;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

/** Synchronized UTF-8 JSONL event artifact using only JDK APIs. */
public final class JsonlNetworkEventSink implements NetworkEventSink {
    private final BufferedWriter writer;
    private boolean closed;

    public JsonlNetworkEventSink(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }

    @Override
    public synchronized void record(NetworkEvent event) {
        Objects.requireNonNull(event, "event");
        if (closed) throw new IllegalStateException("Network event log is closed");
        try {
            writer.write(encode(event));
            writer.newLine();
            if (event.type().contains("FAILED") || event.type().contains("ABORT")
                    || event.type().equals("PROXY_STARTED") || event.type().equals("PROXY_STOPPED")) {
                writer.flush();
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not write network event", exception);
        }
    }

    private static String encode(NetworkEvent event) {
        StringBuilder json = new StringBuilder(256);
        json.append('{');
        field(json, "schema", "modbench-network-event-1", false);
        number(json, "monotonicOffsetNanos", event.monotonicOffsetNanos());
        number(json, "connectionId", event.connectionId());
        nullableField(json, "direction", event.direction() == null ? null : event.direction().name());
        field(json, "type", event.type(), true);
        number(json, "streamOffset", event.streamOffset());
        number(json, "queueBytes", event.queueBytes());
        nullableField(json, "detail", event.detail());
        return json.append('}').toString();
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        try {
            writer.close();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not close network event log", exception);
        }
    }

    private static void field(StringBuilder json, String name, String value, boolean comma) {
        if (comma) json.append(',');
        string(json, name);
        json.append(':');
        string(json, Objects.requireNonNull(value, name));
    }

    private static void nullableField(StringBuilder json, String name, String value) {
        json.append(',');
        string(json, name);
        json.append(':');
        if (value == null) json.append("null");
        else string(json, value);
    }

    private static void number(StringBuilder json, String name, long value) {
        json.append(',');
        string(json, name);
        json.append(':').append(value);
    }

    private static void string(StringBuilder json, String value) {
        json.append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> json.append("\\\"");
                case '\\' -> json.append("\\\\");
                case '\b' -> json.append("\\b");
                case '\f' -> json.append("\\f");
                case '\n' -> json.append("\\n");
                case '\r' -> json.append("\\r");
                case '\t' -> json.append("\\t");
                default -> {
                    if (character < 0x20) json.append(String.format("\\u%04x", (int) character));
                    else json.append(character);
                }
            }
        }
        json.append('"');
    }
}