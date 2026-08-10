package com.zhongbai233.bench.api.neoforge.client;

import com.zhongbai233.bench.api.client.gui.BenchGuiSelection;
import com.zhongbai233.bench.api.client.gui.BenchGuiCaptureOptions;
import com.zhongbai233.bench.api.client.gui.BenchGuiSelector;
import com.zhongbai233.bench.api.client.gui.BenchGuiSelectors;
import com.zhongbai233.bench.api.client.gui.BenchScreenSnapshot;
import net.minecraft.client.gui.components.events.GuiEventListener;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/**
 * Scenario-scoped read-only GUI debugging session for one expected Screen type.
 * All methods must be called from the Minecraft client thread; returned snapshots are detached.
 */
public interface BenchGuiSession extends AutoCloseable {
    /** Registers a stable scenario-local semantic name for a live interaction element. */
    BenchGuiSession name(GuiEventListener listener, String semanticName);

    /** Captures a detached interaction-tree snapshot of the currently expected Screen. */
    BenchScreenSnapshot snapshot();

    /** Selects one node without ever silently resolving ambiguous matches. */
    default BenchGuiSelection select(BenchGuiSelector selector) {
        return BenchGuiSelectors.select(snapshot(), selector);
    }

    /** Captures the uniquely selected interaction element from a completed frame. */
    CompletableFuture<Path> captureWidget(String name, BenchGuiSelector selector,
                                          BenchGuiCaptureOptions options);

    /** Captures with {@link BenchGuiCaptureOptions#defaults()}. */
    default CompletableFuture<Path> captureWidget(String name, BenchGuiSelector selector) {
        return captureWidget(name, selector, BenchGuiCaptureOptions.defaults());
    }

    /** Whether this session still owns the runtime GUI expectation. */
    boolean active();

    /** Releases the expected-Screen allowance and all live-widget semantic registrations. */
    @Override void close();
}