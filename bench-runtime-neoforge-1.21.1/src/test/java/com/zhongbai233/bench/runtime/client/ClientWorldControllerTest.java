package com.zhongbai233.bench.runtime.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zhongbai233.bench.runtime.RuntimeConfiguration;
import java.nio.file.Path;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.WorldDataConfiguration;
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
    void legacyLevelSettingsKeepTheCreativePeacefulBenchmarkBaseline() {
        var settings = ClientWorldController.levelSettings("isolated-benchmark");
        assertEquals("isolated-benchmark", settings.levelName());
        assertEquals(GameType.CREATIVE, settings.gameType());
        assertEquals(Difficulty.PEACEFUL, settings.difficulty());
        assertFalse(settings.hardcore());
        assertTrue(settings.allowCommands());
        assertEquals(WorldDataConfiguration.DEFAULT, settings.getDataConfiguration());
    }

    @Test
    void worldSettingsDoNotShareMutableGameRules() {
        var first = ClientWorldController.levelSettings("first");
        var second = ClientWorldController.levelSettings("second");
        assertNotSame(first.gameRules(), second.gameRules());
        first.gameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, null);
        assertFalse(first.gameRules().getBoolean(GameRules.RULE_DAYLIGHT));
        assertTrue(second.gameRules().getBoolean(GameRules.RULE_DAYLIGHT));
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
