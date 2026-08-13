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

    private static RuntimeConfiguration configuration(int index, int count) {
        return new RuntimeConfiguration(Path.of("results"), 1, 7L, 200L, "target",
                "modbench-client-world", false, 1280, 720, false, 260, 12, 12,
                false, 2.0, 900, "normal", "overworld", "minecraft:normal", "",
                false, "", "remote-client", "session", index, count);
    }
}
