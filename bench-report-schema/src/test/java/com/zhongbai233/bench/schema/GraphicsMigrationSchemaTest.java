package com.zhongbai233.bench.schema;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

class GraphicsMigrationSchemaTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String[] SCENES = {
        "rgba-stride-orientation", "rg8-uv-channels", "buffer-reuse",
        "shader-depth-blend", "offscreen-copy", "resource-recreate"
    };

    @Test
    void validSuiteIncludesSixGpuScenesAndOptionalReloadSkip() throws Exception {
        assertValid(report());
    }

    @Test
    void optionalSceneCanBeOmittedButRequiredSceneCannot() throws Exception {
        ObjectNode report = report();
        scenes(report).remove(6);
        assertValid(report);
        scenes(report).remove(0);
        assertInvalid(report);
    }

    @Test
    void duplicateSceneCannotReplaceARequiredScene() throws Exception {
        ObjectNode report = report();
        scenes(report).set(1, scenes(report).get(0).deepCopy());
        assertInvalid(report);
    }

    @Test
    void optionalSceneCannotBeDuplicated() throws Exception {
        ObjectNode report = report();
        scenes(report).add(scenes(report).get(6).deepCopy());
        assertInvalid(report);
    }

    @Test
    void frozenSuiteAndSceneIdentityCannotDrift() throws Exception {
        for (String field : new String[] {"schemaVersion", "suiteId", "scenarioId"}) {
            ObjectNode report = report();
            report.put(field, "other");
            assertInvalid(report);
        }
        for (String field : new String[] {"suiteRevision"}) {
            ObjectNode report = report();
            report.put(field, 2);
            assertInvalid(report);
        }
        for (String field : new String[] {"width", "height", "revision", "channels", "tolerancePerChannel", "maxBadPixelRatio"}) {
            ObjectNode report = report();
            scene(report, 0).put(field, 999);
            assertInvalid(report);
        }
    }

    @Test
    void seedMayBeOverriddenButMustRemainAnInteger() throws Exception {
        ObjectNode report = report();
        report.put("seed", 42);
        assertValid(report);
        report.put("seed", true);
        assertInvalid(report);
    }

    @Test
    void passingReportCannotCarryEnvironmentInvalidations() throws Exception {
        ObjectNode report = report();
        report.put("environmentValid", true).putArray("invalidations");
        assertValid(report);
        report.put("environmentValid", false);
        assertInvalid(report);
        report.put("environmentValid", true).withArray("invalidations").add("device lost");
        assertInvalid(report);
        report.put("status", "BLOCKED");
        assertValid(report);
    }

    @Test
    void requiredSceneCannotSkipOrHideFailureInPassReport() throws Exception {
        ObjectNode report = report();
        scene(report, 0).put("status", "SKIP").put("reason", "unsupported");
        assertInvalid(report);
        scene(report, 0).put("status", "FAIL");
        assertInvalid(report);
        report.put("status", "FAIL");
        assertValid(report);
    }

    @Test
    void blockedSuiteCanOmitArtifactsButNeedsReasons() throws Exception {
        ObjectNode report = report();
        report.put("status", "BLOCKED");
        ObjectNode scene = scene(report, 0);
        scene.put("status", "BLOCKED").put("reason", "No render device");
        for (String field : new String[] {"expectedSha256", "actualSha256", "expectedArtifact", "actualArtifact"}) {
            scene.put(field, "");
        }
        assertValid(report);
        scene.put("reason", "  ");
        assertInvalid(report);
    }

    @Test
    void passingScenesRequireNonemptyArtifactHashPairs() throws Exception {
        ObjectNode report = report();
        scene(report, 0).put("actualArtifact", "").put("actualSha256", "");
        assertInvalid(report);
        report.put("status", "FAIL");
        scene(report, 0).put("status", "FAIL").put("reason", "readback failed");
        assertValid(report);
        scene(report, 0).put("actualSha256", "a".repeat(64));
        assertInvalid(report);
    }

    @Test
    void optionalSceneMustRemainZeroSizeSkipWithReason() throws Exception {
        ObjectNode report = report();
        scene(report, 6).put("reason", "");
        assertInvalid(report);
        scene(report, 6).put("reason", "unsupported").put("width", 17);
        assertInvalid(report);
        scene(report, 6).put("width", 0).put("status", "PASS");
        assertInvalid(report);
    }

    @Test
    void artifactNamesAndHashesAreStrict() throws Exception {
        for (String path : new String[] {"../escape.rgba", "/absolute.rgba", "sub/image.rgba", "C:\\image.rgba", "image.png"}) {
            ObjectNode report = report();
            scene(report, 0).put("actualArtifact", path);
            assertInvalid(report);
        }
        ObjectNode report = report();
        scene(report, 0).put("actualSha256", "A".repeat(64));
        assertInvalid(report);
    }

    @Test
    void checksAndMetricsHaveSpecifiedTypes() throws Exception {
        ObjectNode report = report();
        scene(report, 0).withObject("checks").put("readback", true);
        assertInvalid(report);
        report = report();
        scene(report, 0).withObject("metrics").put("bytes", "612");
        assertInvalid(report);
    }

    @Test
    void passRequiresCompleteEnvironmentAndUnknownMetadataIsAllowed() throws Exception {
        ObjectNode report = report();
        report.putObject("additionalMetadata").put("futureField", true);
        assertValid(report);
        report.withObject("environment").put("deviceName", " ");
        assertInvalid(report);
    }

    private static ObjectNode report() {
        ObjectNode report = JSON.createObjectNode();
        report.put("schemaVersion", "graphics-migration/1").put("suiteId", "graphics-migration")
                .put("suiteRevision", 1).put("seed", 602263)
                .put("scenarioId", "graphics-migration.suite").put("status", "PASS");
        report.putObject("environment").put("minecraft", "26.2").put("requestedBackend", "opengl")
                .put("actualBackend", "opengl").put("deviceName", "Test GPU")
                .put("vendor", "Test Vendor").put("driver", "Test Driver");
        ArrayNode scenes = report.putArray("scenes");
        for (String id : SCENES) {
            boolean small = id.equals("buffer-reuse") || id.equals("shader-depth-blend");
            int tolerance = id.equals("rg8-uv-channels") || id.equals("shader-depth-blend") ? 2 : 0;
            ObjectNode scene = scenes.addObject();
            scene.put("id", id).put("revision", 1).put("status", "PASS").put("reason", "")
                    .put("width", small ? 8 : 17).put("height", small ? 8 : 9).put("channels", 4)
                    .put("tolerancePerChannel", tolerance).put("maxBadPixelRatio", 0)
                    .put("expectedSha256", "a".repeat(64)).put("actualSha256", "a".repeat(64))
                    .put("expectedArtifact", id + "-expected.rgba").put("actualArtifact", id + "-actual.rgba");
            scene.putObject("checks").put("readback", "PASS");
            scene.putObject("metrics").put("bytes", (small ? 64 : 153) * 4);
        }
        ObjectNode optional = scenes.addObject();
        optional.put("id", "engine-resource-reload").put("revision", 1).put("status", "SKIP")
                .put("reason", "No stable public resource reload hook").put("width", 0).put("height", 0)
                .put("channels", 4).put("tolerancePerChannel", 0).put("maxBadPixelRatio", 0)
                .put("expectedSha256", "").put("actualSha256", "")
                .put("expectedArtifact", "").put("actualArtifact", "");
        optional.putObject("checks");
        optional.putObject("metrics");
        return report;
    }

    private static ArrayNode scenes(ObjectNode report) {
        return (ArrayNode) report.get("scenes");
    }

    private static ObjectNode scene(ObjectNode report, int index) {
        return (ObjectNode) scenes(report).get(index);
    }

    private static JsonSchema schema() throws Exception {
        try (InputStream stream = GraphicsMigrationSchemaTest.class.getResourceAsStream("/schema/graphics-migration-1.schema.json")) {
            if (stream == null) {
                throw new AssertionError("Missing graphics migration schema resource");
            }
            return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(JSON.readTree(stream));
        }
    }

    private static void assertValid(ObjectNode report) throws Exception {
        var errors = schema().validate(report);
        assertTrue(errors.isEmpty(), errors::toString);
    }

    private static void assertInvalid(ObjectNode report) throws Exception {
        assertFalse(schema().validate(report).isEmpty());
    }
}
