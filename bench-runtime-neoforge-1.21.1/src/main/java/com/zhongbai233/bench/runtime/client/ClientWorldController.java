package com.zhongbai233.bench.runtime.client;

import com.zhongbai233.bench.runtime.RuntimeConfiguration;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorPresets;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Opens or creates the isolated integrated world using Minecraft's public 1.21.1 flow. */
final class ClientWorldController {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientWorldController.class);
    private boolean requested;

    boolean requestIfNeeded(Minecraft minecraft, RuntimeConfiguration configuration) {
        if (!configuration.clientAutoWorld() || minecraft.level != null || requested) return false;
        requested = true;
        String worldId = effectiveWorldId(configuration);
        LOGGER.info("MODBENCH event=world_open world={} preset={} seed={}",
                worldId, configuration.clientWorldPreset(), configuration.seed());
        Runnable onFailure = () -> {
            LOGGER.error("MODBENCH event=run_failed phase=world_open world={}", worldId);
            minecraft.stop();
        };
        if (minecraft.getLevelSource().levelExists(worldId)) {
            minecraft.createWorldOpenFlows().openWorld(worldId, onFailure);
        } else {
            LevelSettings settings = levelSettings(worldId);
            minecraft.createWorldOpenFlows().createFreshLevel(
                    worldId,
                    settings,
                    new WorldOptions(configuration.seed(), false, false),
                    dimensionsFor(configuration.clientWorldPreset()),
                    minecraft.screen);
        }
        return true;
    }

    /**
     * Non-default presets get their own world directory, so switching presets can never silently
     * reuse a world generated with different settings.
     */
    static String effectiveWorldId(RuntimeConfiguration configuration) {
        String preset = configuration.clientWorldPreset();
        return preset.equals("normal") ? configuration.clientWorldId()
                : configuration.clientWorldId() + "-" + preset;
    }

    static LevelSettings levelSettings(String worldId) {
        return new LevelSettings(
                worldId,
                GameType.CREATIVE,
                false,
                Difficulty.PEACEFUL,
                true,
                new GameRules(),
                WorldDataConfiguration.DEFAULT);
    }

    private static Function<RegistryAccess, WorldDimensions> dimensionsFor(String preset) {
        return switch (preset) {
            case "flat" -> ClientWorldController::createFlatWorldDimensions;
            case "void" -> ClientWorldController::createVoidWorldDimensions;
            default -> WorldPresets::createNormalWorldDimensions;
        };
    }

    private static WorldDimensions createFlatWorldDimensions(RegistryAccess registries) {
        return registries.registryOrThrow(Registries.WORLD_PRESET)
                .getOrThrow(WorldPresets.FLAT)
                .createWorldDimensions();
    }

    /**
     * The vanilla "The Void" superflat: one air layer, void biome, and the small stone spawn
     * platform. Terrain cost drops to zero so scenarios measure only their own content.
     */
    private static WorldDimensions createVoidWorldDimensions(RegistryAccess registries) {
        FlatLevelGeneratorSettings voidSettings = registries
                .registryOrThrow(Registries.FLAT_LEVEL_GENERATOR_PRESET)
                .getOrThrow(FlatLevelGeneratorPresets.THE_VOID)
                .settings();
        return createFlatWorldDimensions(registries)
                .replaceOverworldGenerator(registries, new FlatLevelSource(voidSettings));
    }
}
