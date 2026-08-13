package com.zhongbai233.bench.runtime.client;

import com.mojang.blaze3d.platform.Window;
import com.zhongbai233.bench.api.neoforge.client.BenchClientEnvironment;
import com.zhongbai233.bench.api.neoforge.client.BenchClientReadiness;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Watches for conditions that make a client measurement incomparable.
 *
 * <p>The guard only arms once the render pipeline reported ready for the first time, so the normal
 * terrain-loading screen and resource reload of a fresh world are not mistaken for interference.
 */
final class ClientEnvironmentGuard implements BenchClientEnvironment {
    private final Minecraft minecraft;
    private final ClientReadinessGate readinessGate = new ClientReadinessGate();
    private final FrameStabilityMonitor stability;
    private final boolean requireWindowFocus;
    private final List<String> invalidations = new ArrayList<>();
    private final Set<String> seenReasons = new LinkedHashSet<>();
    private BenchClientReadiness readiness =
            new BenchClientReadiness(false, "not_sampled", 0, 0, 0, false, false);
    private int baselineWidth;
    private int baselineHeight;
    private boolean armed;
    private long ticksBeforeArmed;
    private Class<? extends Screen> expectedScreen;
    private long guiExpectationGeneration;
    private boolean expectedReconnect;
    private int expectedReconnectCount;

    ClientEnvironmentGuard(Minecraft minecraft, boolean requireWindowFocus, double stableFrameRatio) {
        this.minecraft = Objects.requireNonNull(minecraft, "minecraft");
        this.requireWindowFocus = requireWindowFocus;
        stability = new FrameStabilityMonitor(stableFrameRatio);
    }

    void recordFrame(long timestampNanos) {
        stability.recordFrame(timestampNanos);
    }

    /** Samples the environment once per client tick. */
    void sample() {
        boolean expectedScreenOpen = minecraft.screen != null
                && (isExpectedScreen(minecraft.screen) || expectedReconnect);
        readiness = readinessGate.evaluate(minecraft, expectedScreenOpen);
        Window window = minecraft.getWindow();
        if (!armed) {
            ticksBeforeArmed++;
            if (!readiness.ready()) return;
            armed = true;
            baselineWidth = window.getWidth();
            baselineHeight = window.getHeight();
            return;
        }
        if (expectedReconnect && minecraft.level != null && minecraft.player != null
                && minecraft.screen == null && readiness.ready()) {
            expectedReconnect = false;
        }
        if (!expectedReconnect) checkScreenExpectation();
        if (window.getWidth() != baselineWidth || window.getHeight() != baselineHeight) {
            invalidate("client.window.resized=" + baselineWidth + "x" + baselineHeight
                    + "->" + window.getWidth() + "x" + window.getHeight());
        }
        if (window.isMinimized() || window.isIconified()) invalidate("client.window.minimized");
        if (requireWindowFocus && !minecraft.isWindowActive()) invalidate("client.window.focus_lost");
        if (minecraft.isPaused()) invalidate("client.game.paused");
        if (minecraft.getOverlay() != null) invalidate("client.overlay.active");
    }

    /** Returns {@code true} once the render pipeline reported ready at least once. */
    boolean isArmed() {
        return armed;
    }

    long ticksBeforeArmed() {
        return ticksBeforeArmed;
    }

    boolean requiresWindowFocus() {
        return requireWindowFocus;
    }

    long beginExpectedScreen(Class<? extends Screen> screenClass) {
        expectedScreen = Objects.requireNonNull(screenClass, "screenClass");
        return ++guiExpectationGeneration;
    }

    void endExpectedScreen(long generation) {
        if (generation == guiExpectationGeneration) expectedScreen = null;
    }

    void clearExpectedScreen() {
        expectedScreen = null;
        guiExpectationGeneration++;
    }

    /** Allows the loading/connect screens caused by one benchmark-requested physical reconnect. */
    void beginExpectedReconnect() {
        if (!expectedReconnect) expectedReconnectCount++;
        expectedReconnect = true;
    }

    int expectedReconnectCount() {
        return expectedReconnectCount;
    }

    private boolean isExpectedScreen(Screen screen) {
        return expectedScreen != null && expectedScreen.isInstance(screen);
    }

    private void checkScreenExpectation() {
        if (expectedScreen != null) {
            if (minecraft.screen == null) invalidate("client.screen.expected_but_closed=" + expectedScreen.getName());
            else if (!isExpectedScreen(minecraft.screen)) {
                invalidate("client.screen.expected=" + expectedScreen.getName()
                        + ",actual=" + minecraft.screen.getClass().getName());
            }
        } else if (minecraft.screen != null) {
            invalidate("client.screen.open=" + minecraft.screen.getClass().getName());
        }
    }

    @Override
    public boolean isValid() {
        return invalidations.isEmpty();
    }

    @Override
    public List<String> invalidations() {
        return List.copyOf(invalidations);
    }

    @Override
    public void invalidate(String reason) {
        Objects.requireNonNull(reason, "reason");
        if (seenReasons.add(reason)) invalidations.add(reason);
    }

    @Override
    public BenchClientReadiness readiness() {
        return readiness;
    }

    @Override
    public boolean isFrameStable(int requiredFrames) {
        return stability.isStable(requiredFrames);
    }
}
