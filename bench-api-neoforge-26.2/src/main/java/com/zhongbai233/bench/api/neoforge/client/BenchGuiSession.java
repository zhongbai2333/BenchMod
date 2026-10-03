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
 * Scenario-scoped GUI inspection and interaction session for one expected Screen type.
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

    /** Waits until the selector resolves uniquely, failing immediately if it is ambiguous. */
    default BenchClientStepResult await(BenchGuiSelector selector) {
        BenchGuiSelection selection = select(selector);
        return switch (selection.status()) {
            case MATCHED -> BenchClientStepResult.COMPLETE;
            case NOT_FOUND, INDEX_OUT_OF_RANGE -> BenchClientStepResult.CONTINUE;
            case AMBIGUOUS -> throw new IllegalStateException(
                    "GUI selector is ambiguous: " + selection.candidatePaths());
        };
    }

    /** Waits until no node matches the selector. */
    default BenchClientStepResult awaitMissing(BenchGuiSelector selector) {
        return switch (select(selector).status()) {
            case NOT_FOUND, INDEX_OUT_OF_RANGE -> BenchClientStepResult.COMPLETE;
            case MATCHED, AMBIGUOUS -> BenchClientStepResult.CONTINUE;
        };
    }

    /** Clicks the center of the uniquely selected node with the primary mouse button. */
    default boolean click(BenchGuiSelector selector) {
        return click(selector, 0, 0);
    }

    /** Clicks the center of the uniquely selected node with the requested button and modifiers. */
    boolean click(BenchGuiSelector selector, int button, int modifiers);

    /** Sends two primary-button clicks, marking the second as a double click. */
    boolean doubleClick(BenchGuiSelector selector);

    /** Scrolls at the center of the uniquely selected node. */
    boolean scroll(BenchGuiSelector selector, double horizontal, double vertical);

    /** Drags from the selected node's center with the primary mouse button. */
    default boolean drag(BenchGuiSelector selector, double deltaX, double deltaY) {
        return drag(selector, deltaX, deltaY, 0, 0);
    }

    /** Drags from the selected node's center by a logical-GUI-coordinate delta. */
    boolean drag(BenchGuiSelector selector, double deltaX, double deltaY,
                 int button, int modifiers);

    /** Presses and releases a key with no scancode or modifiers. */
    default boolean pressKey(int key) {
        return pressKey(key, 0, 0);
    }

    /** Presses and releases a key through the active Screen and NeoForge input hooks. */
    boolean pressKey(int key, int scancode, int modifiers);

    /** Types Unicode code points into the currently focused GUI element. */
    boolean typeText(String text);

    /** Clicks a uniquely selected element to focus it, then types Unicode text. */
    default boolean typeText(BenchGuiSelector selector, String text) {
        return click(selector) | typeText(text);
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
