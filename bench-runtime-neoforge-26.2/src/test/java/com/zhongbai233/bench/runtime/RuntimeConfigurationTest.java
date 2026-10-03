package com.zhongbai233.bench.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RuntimeConfigurationTest {
    @Test
    void remoteClientRunTypesAreBackwardCompatibleAndDistinct() {
        assertEquals("remote-client", configuration(0, 1).participantRunType());
        assertEquals("remote-client-0", configuration(0, 2).participantRunType());
        assertEquals("remote-client-1", configuration(1, 2).participantRunType());
    }

    @Test
    void invalidPairedCoordinatesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> configuration(-1, 2));
        assertThrows(IllegalArgumentException.class, () -> configuration(2, 2));
        assertThrows(IllegalArgumentException.class, () -> configuration(0, 0));
    }

    @Test
    void legacyConstructorsKeepTheOpenGlBaseline() {
        assertEquals("opengl", configuration(0, 1).clientGraphicsBackend());
    }

    @Test
    void graphicsBackendIsExplicitAndValidated() {
        RuntimeConfiguration base = configuration(0, 1);
        assertEquals("vulkan", withBackend(base, " VULKAN ").clientGraphicsBackend());
        assertThrows(IllegalArgumentException.class, () -> withBackend(base, "auto"));
        assertThrows(IllegalArgumentException.class, () -> withBackend(base, "metal"));
    }

    private static RuntimeConfiguration withBackend(RuntimeConfiguration base, String backend) {
        return new RuntimeConfiguration(base.resultDirectory(), base.expectedProviderCount(), base.seed(),
                base.phaseTimeoutTicks(), base.targetMod(), base.clientWorldId(), base.clientAutoWorld(),
                base.clientWindowWidth(), base.clientWindowHeight(), base.clientVsync(), base.clientFpsLimit(),
                base.clientRenderDistance(), base.clientSimulationDistance(), base.clientRequireWindowFocus(),
                base.clientStableFrameRatio(), base.clientCaptureGateFrameBudget(), base.clientWorldPreset(),
                base.clientDimension(), base.serverLevelType(), base.serverGeneratorSettings(), base.jfrEnabled(),
                base.scenarioFilter(), base.participantMode(), base.pairedSessionId(), base.pairedClientIndex(),
                base.pairedClientCount(), backend);
    }

    private static RuntimeConfiguration configuration(int index, int count) {
        return new RuntimeConfiguration(Path.of("results"), 1, 7L, 200L, "target",
                "modbench-client-world", false, 1280, 720, false, 260, 12, 12,
                false, 2.0, 900, "normal", "overworld", "minecraft:normal", "",
                false, "", "remote-client", "session", index, count);
    }
}
