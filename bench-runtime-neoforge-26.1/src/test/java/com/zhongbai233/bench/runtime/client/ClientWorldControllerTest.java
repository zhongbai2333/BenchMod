package com.zhongbai233.bench.runtime.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.zhongbai233.bench.runtime.RuntimeConfiguration;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ClientWorldControllerTest {
    @Test
    void defaultPresetKeepsTheConfiguredWorldId() {
        assertEquals("modbench-client-world", ClientWorldController.effectiveWorldId(configuration("normal")));
    }

    @Test
    void nonDefaultPresetsGetTheirOwnWorldDirectory() {
        assertEquals("modbench-client-world-flat", ClientWorldController.effectiveWorldId(configuration("flat")));
        assertEquals("modbench-client-world-void", ClientWorldController.effectiveWorldId(configuration("void")));
    }

    @Test
    void unknownPresetOrDimensionIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> configuration("amplified"));
        assertThrows(IllegalArgumentException.class,
                () -> configuration("normal", "the_moon", "minecraft:normal"));
        assertThrows(IllegalArgumentException.class,
                () -> configuration("normal", "overworld", " "));
    }

    private static RuntimeConfiguration configuration(String preset) {
        return configuration(preset, "overworld", "minecraft:normal");
    }

    private static RuntimeConfiguration configuration(String preset, String dimension, String levelType) {
        return new RuntimeConfiguration(
                Path.of("results"), 1, 7L, 200L, "target", "modbench-client-world", true,
                1280, 720, false, 260, 12, 12, true, 2.0, 900, preset, dimension, levelType, "", false, "");
    }
}
