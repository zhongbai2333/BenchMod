package com.zhongbai233.bench.runtime.client;

import com.zhongbai233.bench.runtime.RuntimeConfiguration;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Places the benchmark player into the configured dimension before scenarios start.
 *
 * <p>The teleport runs server-side on the integrated server; the controller then waits until the
 * client level actually switched and a fresh {@code LocalPlayer} exists. Scenarios position the
 * player themselves, so the anchor only decides which chunks load first.
 */
final class ClientDimensionController {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientDimensionController.class);
    /** Ticks between teleport attempts while the dimension switch is in flight. */
    private static final long RETRY_TICKS = 100;

    private long lastRequestTick = Long.MIN_VALUE;
    private long ticks;

    /** Returns {@code true} once the local player is in the configured dimension. */
    boolean isReady(Minecraft minecraft, RuntimeConfiguration configuration) {
        ResourceKey<Level> target = targetDimension(configuration.clientDimension());
        if (minecraft.level == null || minecraft.player == null) return false;
        if (minecraft.level.dimension() == target) return true;
        ticks++;
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (server == null) return false;
        if (ticks - lastRequestTick < RETRY_TICKS && lastRequestTick != Long.MIN_VALUE) return false;
        lastRequestTick = ticks;
        LOGGER.info("MODBENCH event=dimension_teleport target={}", target.identifier());
        server.execute(() -> teleport(server, target));
        return false;
    }

    private static void teleport(IntegratedServer server, ResourceKey<Level> target) {
        var players = server.getPlayerList().getPlayers();
        ServerLevel level = server.getLevel(target);
        if (players.isEmpty() || level == null) {
            LOGGER.warn("MODBENCH event=dimension_teleport_deferred target={}", target.identifier());
            return;
        }
        ServerPlayer player = players.get(0);
        double[] anchor = anchorFor(target);
        player.teleportTo(level, anchor[0], anchor[1], anchor[2], Set.of(), 0.0F, 0.0F, false);
    }

    static ResourceKey<Level> targetDimension(String dimension) {
        return switch (dimension) {
            case "the_nether" -> Level.NETHER;
            case "the_end" -> Level.END;
            default -> Level.OVERWORLD;
        };
    }

    /**
     * Fixed arrival points: above the end's main island, and inside the nether at a height that
     * exists on every seed. Creative mode ignores suffocation, and scenarios set their own pose.
     */
    private static double[] anchorFor(ResourceKey<Level> target) {
        if (target == Level.END) return new double[] {0.5, 80.0, 0.5};
        return new double[] {0.5, 100.0, 0.5};
    }
}
