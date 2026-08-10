package com.zhongbai233.bench.network.proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zhongbai233.bench.network.NetworkDirection;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsonlNetworkEventSinkTest {
    @TempDir Path directory;

    @Test
    void writesOneEscapedJsonObjectPerEvent() throws Exception {
        Path target = directory.resolve("nested/network-events.jsonl");
        JsonlNetworkEventSink sink = new JsonlNetworkEventSink(target);
        try (sink) {
            sink.record(new NetworkEvent(12, 3, NetworkDirection.CLIENT_TO_SERVER,
                "FAULT_STARTED", 42, 100, "quote=\" slash=\\ controls=\b\f\n\r\t\u0000\u001f"));
            sink.record(new NetworkEvent(15, 3, null, "PROXY_STOPPED", 0, 0, null));
        }
        var lines = Files.readAllLines(target);
        assertEquals(2, lines.size());
        assertTrue(lines.get(0).contains("\"direction\":\"CLIENT_TO_SERVER\""));
        assertTrue(lines.get(0).contains("quote=\\\" slash=\\\\ controls=\\b\\f\\n\\r\\t\\u0000\\u001f"));
        assertTrue(lines.get(1).contains("\"direction\":null"));
        assertTrue(lines.get(1).contains("\"detail\":null"));
        assertThrows(IllegalStateException.class, () -> sink.record(
            new NetworkEvent(16, 3, null, "LATE_EVENT", 0, 0, null)));
    }
}