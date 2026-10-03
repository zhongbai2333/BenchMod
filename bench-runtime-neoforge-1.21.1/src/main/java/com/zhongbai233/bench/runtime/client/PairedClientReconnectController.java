package com.zhongbai233.bench.runtime.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Re-enters the same paired server after an in-scenario physical disconnect. */
final class PairedClientReconnectController {
    private static final Logger LOGGER = LoggerFactory.getLogger(PairedClientReconnectController.class);
    private static final int INITIAL_RETRY_TICKS = 5;
    private static final int RETRY_INTERVAL_TICKS = 100;

    private ServerData server;
    private int lastAttemptTick = Integer.MIN_VALUE;

    void observeConnected(Minecraft minecraft) {
        ServerData current = minecraft.getCurrentServer();
        if (current != null) {
            server = current;
        }
        lastAttemptTick = Integer.MIN_VALUE;
    }

    boolean requestIfNeeded(Minecraft minecraft, int unavailableTicks) {
        if (server == null || unavailableTicks < INITIAL_RETRY_TICKS
                || minecraft.screen instanceof ConnectScreen
                || lastAttemptTick != Integer.MIN_VALUE
                        && unavailableTicks - lastAttemptTick < RETRY_INTERVAL_TICKS) {
            return false;
        }
        lastAttemptTick = unavailableTicks;
        LOGGER.info("MODBENCH event=paired_client_reconnect_attempt tick={} server={}",
                unavailableTicks, server.ip);
        ConnectScreen.startConnecting(minecraft.screen, minecraft,
                ServerAddress.parseString(server.ip), server, false, null);
        return true;
    }
}
