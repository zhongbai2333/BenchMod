package com.zhongbai233.bench.runtime.client;

import com.zhongbai233.bench.runtime.ModBenchRuntimeMod;
import com.zhongbai233.bench.runtime.RuntimeConfiguration;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Physical-client-only bridge. The class is not loaded on dedicated servers. */
@EventBusSubscriber(modid = ModBenchRuntimeMod.MOD_ID, value = Dist.CLIENT)
public final class NeoForgeClientAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(NeoForgeClientAdapter.class);
    private static ClientBenchEngine engine;
    private static boolean attempted;
    private static RuntimeConfiguration configuration;
    private static long startupTicks;
    private static final ClientWorldController WORLD = new ClientWorldController();
    private static final ClientGraphicsController GRAPHICS = new ClientGraphicsController();
    private static final ClientDimensionController DIMENSION = new ClientDimensionController();
    private static final PairedClientReconnectController RECONNECT = new PairedClientReconnectController();
    private static long reconnectTicks;

    private NeoForgeClientAdapter() {}

    @SubscribeEvent
    public static void clientTickPost(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (configuration == null) {
            try {
                configuration = RuntimeConfiguration.fromSystemProperties();
                GRAPHICS.applyOnce(minecraft, configuration);
            } catch (Exception exception) {
                attempted = true;
                LOGGER.error("MODBENCH event=run_failed phase=client_configuration", exception);
                minecraft.stop();
                return;
            }
        }
        GRAPHICS.keepMouseReleased(minecraft);
        if (!attempted && minecraft.level == null) {
            WORLD.requestIfNeeded(minecraft, configuration);
            startupTicks++;
            if (startupTicks >= configuration.phaseTimeoutTicks()) {
                attempted = true;
                LOGGER.error("MODBENCH event=run_failed phase=world_ready timeout_ticks={}", startupTicks);
                minecraft.stop();
                return;
            }
        }
        if (engine == null && !attempted && minecraft.level != null && minecraft.player != null
                && !DIMENSION.isReady(minecraft, configuration)) {
            // The world is up but the player is still travelling to the configured dimension.
            startupTicks++;
            if (startupTicks >= configuration.phaseTimeoutTicks()) {
                attempted = true;
                LOGGER.error("MODBENCH event=run_failed phase=dimension_ready timeout_ticks={}", startupTicks);
                minecraft.stop();
            }
            return;
        }
        if (engine == null && !attempted && minecraft.level != null && minecraft.player != null) {
            attempted = true;
            try {
                LOGGER.info("MODBENCH event=run_start side={} session={}",
                    configuration.remoteClient() ? "remote_client" : "integrated_client",
                    configuration.pairedSessionId());
                engine = ClientBenchEngine.start(minecraft, configuration);
                engine.recordGraphicsSnapshot("start", GRAPHICS.snapshot(minecraft));
            } catch (Exception exception) {
                LOGGER.error("MODBENCH event=run_failed phase=discover", exception);
                minecraft.stop();
                return;
            }
        }
        if (engine == null) return;
        if (minecraft.level == null || minecraft.player == null) {
            if (configuration.remoteClient()) {
                if (reconnectTicks == 0L) engine.beginExpectedReconnect();
                reconnectTicks++;
                RECONNECT.requestIfNeeded(minecraft, Math.toIntExact(Math.min(Integer.MAX_VALUE, reconnectTicks)));
                if (reconnectTicks >= configuration.phaseTimeoutTicks()) {
                    engine.abort("Paired client reconnect exceeded " + reconnectTicks + " ticks");
                }
                return;
            }
            engine.abort("Client world or player became unavailable");
        } else {
            if (configuration.remoteClient()) {
                RECONNECT.observeConnected(minecraft);
                reconnectTicks = 0L;
            }
            engine.tick();
        }
        if (engine.isComplete()) {
            LOGGER.info("MODBENCH event=run_complete status={}", engine.status());
            engine = null;
            minecraft.stop();
        }
    }

    @SubscribeEvent
    public static void renderFramePre(RenderFrameEvent.Pre event) {
        if (engine != null) engine.recordFrame(System.nanoTime());
    }

    @SubscribeEvent
    public static void renderFramePost(RenderFrameEvent.Post event) {
        if (engine != null && engine.hasPendingScreenshots()) engine.capturePendingScreenshot();
    }
}
