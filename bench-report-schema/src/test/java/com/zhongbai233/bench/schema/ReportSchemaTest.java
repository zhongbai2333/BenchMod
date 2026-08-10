package com.zhongbai233.bench.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.zhongbai233.bench.network.NetworkDirection;
import com.zhongbai233.bench.network.proxy.JsonlNetworkEventSink;
import com.zhongbai233.bench.network.proxy.NetworkEvent;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReportSchemaTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    @TempDir Path directory;

    @Test
    void minimalReportContainsEveryRequiredTopLevelProperty() throws Exception {
        JsonNode schema = resource("/schema/mod-bench-report-1.0.0.schema.json");
        JsonNode example = resource("/examples/minimal-report.json");

        assertEquals("https://json-schema.org/draft/2020-12/schema", schema.required("$schema").textValue());
        assertEquals("https://schemas.zhongbai233.com/mod-bench/report/1.0.0", schema.required("$id").textValue());
        assertEquals("1.0.0", example.required("schema").textValue());
        assertTrue(StreamSupport.stream(schema.required("required").spliterator(), false)
                .map(JsonNode::textValue)
                .allMatch(example::has));
    }

    @Test
    void schemaAndExampleAreValidJsonObjects() throws Exception {
        JsonNode schemaNode = resource("/schema/mod-bench-report-1.0.0.schema.json");
        JsonNode example = resource("/examples/minimal-report.json");
        var schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(schemaNode);

        assertTrue(schema.validate(example).isEmpty());

        JsonNode invalid = example.deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) invalid.path("summary")).put("status", "NOT_A_STATUS");
        assertFalse(schema.validate(invalid).isEmpty());
    }

    @Test
    void validatesExternalRuntimeReportWhenProvided() throws Exception {
        String reportPath = System.getProperty("modBenchReport", "");
        if (reportPath.isBlank()) {
            return;
        }
        JsonNode schemaNode = resource("/schema/mod-bench-report-1.0.0.schema.json");
        var schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(schemaNode);
        JsonNode report = JSON.readTree(Files.readString(Path.of(reportPath)));

        assertTrue(schema.validate(report).isEmpty(), () -> schema.validate(report).toString());
    }

    @Test
    void networkEventSchemaAcceptsValidEventsAndRejectsNegativeQueueMetrics() throws Exception {
        JsonNode schemaNode = resource("/schema/modbench-network-event-1.schema.json");
        var schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(schemaNode);
        JsonNode valid = JSON.readTree("""
                {"schema":"modbench-network-event-1","monotonicOffsetNanos":1,"connectionId":2,
                 "direction":"CLIENT_TO_SERVER","type":"QUANTUM_FORWARDED","streamOffset":0,
                 "queueBytes":0,"detail":null}
                """);
        assertTrue(schema.validate(valid).isEmpty(), () -> schema.validate(valid).toString());
        JsonNode invalid = valid.deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) invalid).put("queueBytes", -1);
        assertFalse(schema.validate(invalid).isEmpty());
    }

    @Test
    void realNetworkEventEmitterProducesIndependentlyValidSchemaLines() throws Exception {
        Path artifact = directory.resolve("events.jsonl");
        try (JsonlNetworkEventSink sink = new JsonlNetworkEventSink(artifact)) {
            sink.record(new NetworkEvent(1, 2, NetworkDirection.CLIENT_TO_SERVER,
                    "QUANTUM_QUEUED", 0, 4, "controls=\"\\\n\u0000 Unicode=中"));
            sink.record(new NetworkEvent(2, 2, NetworkDirection.SERVER_TO_CLIENT,
                    "QUANTUM_FORWARDED", 0, 0, null));
            sink.record(new NetworkEvent(3, 0, null, "PROXY_STOPPED", 0, 0, "done"));
        }
        JsonNode schemaNode = resource("/schema/modbench-network-event-1.schema.json");
        var schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(schemaNode);
        List<String> lines = Files.readAllLines(artifact);
        assertEquals(3, lines.size());
        for (String line : lines) {
            JsonNode emitted = JSON.readTree(line);
            assertTrue(schema.validate(emitted).isEmpty(), () -> schema.validate(emitted).toString());
        }
    }

    private static JsonNode resource(String name) throws Exception {
        try (InputStream stream = ReportSchemaTest.class.getResourceAsStream(name)) {
            if (stream == null) {
                throw new AssertionError("Missing test resource: " + name);
            }
            return JSON.readTree(stream);
        }
    }
}