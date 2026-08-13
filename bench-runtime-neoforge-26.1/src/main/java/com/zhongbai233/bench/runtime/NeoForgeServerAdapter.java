package com.zhongbai233.bench.runtime;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class NeoForgeServerAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(NeoForgeServerAdapter.class);
    private static final int PAIRED_CLIENT_STABLE_TICKS = 20;
    private ServerBenchEngine engine;
    private long tickStartedNanos;
    private RuntimeConfiguration configuration;
    private boolean pairedRunStarted;
    private boolean pairedRunFinished;
    private int pairedClientStableTicks;

    @SubscribeEvent
    public void serverStarted(ServerStartedEvent event) {
        if (!event.getServer().isDedicatedServer()) {
            return;
        }
        try {
                configuration = RuntimeConfiguration.fromSystemProperties();
                LOGGER.info("MODBENCH event=run_start side={} session={}",
                    configuration.pairedServer() ? "paired_server" : "dedicated_server",
                    configuration.pairedSessionId());
                if (!configuration.pairedServer()) {
                    engine = ServerBenchEngine.start(event.getServer(), configuration);
                } else {
                    LOGGER.info("MODBENCH event=paired_server_ready session={} waiting_for_remote_players={}",
                            configuration.pairedSessionId(), configuration.pairedClientCount());
                }
        } catch (Exception exception) {
            LOGGER.error("MODBENCH event=run_failed phase=discover", exception);
            event.getServer().halt(false);
        }
    }

    @SubscribeEvent
    public void serverTickPre(ServerTickEvent.Pre event) {
        if (!event.getServer().isDedicatedServer()) {
            return;
        }
        tickStartedNanos = System.nanoTime();
    }

    @SubscribeEvent
    public void serverTickPost(ServerTickEvent.Post event) {
        if (!event.getServer().isDedicatedServer()) {
            return;
        }
        if (engine == null && !pairedRunStarted && !pairedRunFinished
                && configuration != null && configuration.pairedServer()) {
            int playerCount = event.getServer().getPlayerList().getPlayerCount();
            boolean clientsReady = playerCount == configuration.pairedClientCount()
                    && event.getServer().getPlayerList().getPlayers().stream()
                            .allMatch(player -> !player.isRemoved() && player.isAlive());
            pairedClientStableTicks = clientsReady ? pairedClientStableTicks + 1 : 0;
            if (pairedClientStableTicks >= PAIRED_CLIENT_STABLE_TICKS) {
                try {
                    pairedRunStarted = true;
                    LOGGER.info(
                            "MODBENCH event=paired_server_clients_ready session={} players={} expected={} stable_ticks={}",
                            configuration.pairedSessionId(), playerCount, configuration.pairedClientCount(),
                            pairedClientStableTicks);
                    engine = ServerBenchEngine.start(event.getServer(), configuration);
                } catch (Exception exception) {
                    LOGGER.error("MODBENCH event=run_failed phase=paired_start", exception);
                    event.getServer().halt(false);
                    return;
                }
            }
        }
        if (engine == null) return;
        engine.recordServerTick(System.nanoTime() - tickStartedNanos);
        engine.tick();
        if (engine.isComplete()) {
            LOGGER.info("MODBENCH event=run_complete status={}", engine.status());
            engine = null;
            if (configuration == null || !configuration.pairedServer()) {
                event.getServer().halt(false);
            } else {
                pairedRunFinished = true;
                LOGGER.info("MODBENCH event=paired_server_waiting_for_coordinator session={}",
                        configuration.pairedSessionId());
            }
        }
    }

    @SubscribeEvent
    public void serverStopping(ServerStoppingEvent event) {
        if (event.getServer().isDedicatedServer() && engine != null) {
            engine.abort("Server stopped before benchmark completion");
            engine = null;
        }
    }
}
