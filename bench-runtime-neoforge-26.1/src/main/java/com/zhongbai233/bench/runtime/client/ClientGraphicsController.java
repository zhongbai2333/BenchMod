package com.zhongbai233.bench.runtime.client;

import com.mojang.blaze3d.platform.Window;
import com.zhongbai233.bench.runtime.RuntimeConfiguration;
import net.minecraft.client.InactivityFpsLimit;
import net.minecraft.client.Minecraft;

/** Applies the reproducible client graphics baseline and captures actual runtime state. */
final class ClientGraphicsController {
    private boolean applied;

    void applyOnce(Minecraft minecraft, RuntimeConfiguration configuration) {
        if (applied) return;
        applied = true;
        Window window = minecraft.getWindow();
        if (window.isFullscreen()) window.toggleFullScreen();
        window.setWindowed(configuration.clientWindowWidth(), configuration.clientWindowHeight());
        // An unattended run must never be paused by the pause menu Minecraft opens on lost focus.
        minecraft.options.pauseOnLostFocus = false;
        // The AFK throttle drops to 30/10 FPS after 60s without real input, which an unattended
        // run never produces. MINIMIZED keeps unfocused-but-visible windows at full speed.
        minecraft.options.inactivityFpsLimit().set(InactivityFpsLimit.MINIMIZED);
        minecraft.options.enableVsync().set(configuration.clientVsync());
        minecraft.options.framerateLimit().set(configuration.clientFpsLimit());
        minecraft.options.renderDistance().set(configuration.clientRenderDistance());
        minecraft.options.simulationDistance().set(configuration.clientSimulationDistance());
        minecraft.options.save();
    }

    /**
     * Keeps the cursor free while the benchmark drives the camera. A grabbed mouse would both
     * hold the cursor hostage on the machine and let physical mouse movement rotate the player,
     * which is a nondeterminism source. Minecraft re-grabs on focus changes, so this runs per tick.
     */
    void keepMouseReleased(Minecraft minecraft) {
        if (minecraft.mouseHandler.isMouseGrabbed()) minecraft.mouseHandler.releaseMouse();
    }

    Snapshot snapshot(Minecraft minecraft) {
        Window window = minecraft.getWindow();
        return new Snapshot(
                window.getWidth(),
                window.getHeight(),
                window.isFullscreen(),
                minecraft.isWindowActive(),
                minecraft.isPaused(),
                minecraft.screen != null,
                minecraft.options.enableVsync().get(),
                minecraft.options.framerateLimit().get(),
                minecraft.options.renderDistance().get(),
                minecraft.options.simulationDistance().get(),
                minecraft.options.inactivityFpsLimit().get().getSerializedName(),
                minecraft.mouseHandler.isMouseGrabbed());
    }

    record Snapshot(
            int width,
            int height,
            boolean fullscreen,
            boolean active,
            boolean paused,
            boolean screenOpen,
            boolean vsync,
            int fpsLimit,
            int renderDistance,
            int simulationDistance,
            String inactivityFpsLimit,
            boolean mouseGrabbed) {}
}
