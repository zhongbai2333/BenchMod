package com.zhongbai233.bench.runtime.client;

import com.zhongbai233.bench.api.client.gui.BenchGuiNode;
import com.zhongbai233.bench.api.client.gui.BenchGuiRectangle;
import com.zhongbai233.bench.api.client.gui.BenchScreenSnapshot;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;

/** Maps the live Minecraft interaction tree into detached, platform-neutral snapshot records. */
final class ClientGuiSnapshotter {
    private static final int MAX_DEPTH = 64;
    private static final int MAX_NODES = 10_000;
    private final Minecraft minecraft;

    ClientGuiSnapshotter(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    BenchScreenSnapshot snapshot(Class<? extends Screen> expectedScreen,
                                  Function<GuiEventListener, String> semanticNames) {
        Screen screen = minecraft.screen;
        if (screen == null) throw new IllegalStateException("Expected GUI Screen is not open");
        if (!expectedScreen.isInstance(screen)) {
            throw new IllegalStateException("Expected Screen " + expectedScreen.getName()
                    + " but found " + screen.getClass().getName());
        }
        int guiWidth = minecraft.getWindow().getGuiScaledWidth();
        int guiHeight = minecraft.getWindow().getGuiScaledHeight();
        int framebufferWidth = minecraft.getWindow().getWidth();
        int framebufferHeight = minecraft.getWindow().getHeight();
        List<String> diagnostics = new ArrayList<>();
        Map<GuiEventListener, Boolean> visited = new IdentityHashMap<>();
        Counter counter = new Counter();
        List<BenchGuiNode> roots = snapshotChildren(screen.children(), "screen", 0,
                semanticNames, visited, counter, diagnostics);
        return new BenchScreenSnapshot(screen.getClass().getName(), screen.getTitle().getString(),
                guiWidth, guiHeight, framebufferWidth, framebufferHeight, roots, diagnostics);
    }

    private List<BenchGuiNode> snapshotChildren(List<? extends GuiEventListener> liveChildren, String parentPath,
                                                int depth, Function<GuiEventListener, String> semanticNames,
                                                Map<GuiEventListener, Boolean> visited, Counter counter,
                                                List<String> diagnostics) {
        if (depth > MAX_DEPTH) {
            diagnostics.add("max_depth_exceeded=" + parentPath);
            return List.of();
        }
        List<? extends GuiEventListener> children;
        try {
            children = List.copyOf(liveChildren);
        } catch (RuntimeException exception) {
            diagnostics.add("children_copy_failed=" + parentPath + ":" + exception.getClass().getName());
            return List.of();
        }
        List<BenchGuiNode> result = new ArrayList<>(children.size());
        for (int index = 0; index < children.size(); index++) {
            GuiEventListener listener = children.get(index);
            String path = parentPath + "/children[" + index + "]";
            if (listener == null) {
                diagnostics.add("null_listener=" + path);
                continue;
            }
            if (visited.put(listener, Boolean.TRUE) != null) {
                diagnostics.add("duplicate_listener=" + path);
                continue;
            }
            if (++counter.value > MAX_NODES) {
                diagnostics.add("max_nodes_exceeded=" + MAX_NODES);
                break;
            }
            try {
                result.add(snapshotNode(listener, path, depth, semanticNames, visited, counter, diagnostics));
            } catch (RuntimeException exception) {
                diagnostics.add("node_snapshot_failed=" + path + ":" + exception.getClass().getName());
            }
        }
        return List.copyOf(result);
    }

    private BenchGuiNode snapshotNode(GuiEventListener listener, String path, int depth,
                                      Function<GuiEventListener, String> semanticNames,
                                      Map<GuiEventListener, Boolean> visited, Counter counter,
                                      List<String> diagnostics) {
        ScreenRectangle rectangle = listener.getRectangle();
        BenchGuiRectangle bounds = new BenchGuiRectangle(
                rectangle.left(), rectangle.top(), rectangle.width(), rectangle.height());
        boolean visible = true;
        boolean active = true;
        boolean hovered = false;
        int tabOrder = -1;
        String text = "";
        if (listener instanceof AbstractWidget widget) {
            visible = widget.visible;
            active = widget.active;
            hovered = widget.isHovered();
            tabOrder = widget.getTabOrderGroup();
            text = widget.getMessage().getString();
        }
        List<BenchGuiNode> children = listener instanceof ContainerEventHandler container
                ? snapshotChildren(container.children(), path, depth + 1, semanticNames,
                        visited, counter, diagnostics)
                : List.of();
        String semanticName = semanticNames.apply(listener);
        return new BenchGuiNode(path, role(listener), listener.getClass().getName(),
                semanticName == null ? "" : semanticName, text, bounds,
                visible, active, listener.isFocused(), hovered, tabOrder, children);
    }

    private static String role(GuiEventListener listener) {
        String simpleName = listener.getClass().getSimpleName();
        String normalized = simpleName.replaceAll("([a-z0-9])([A-Z])", "$1-$2").toLowerCase(java.util.Locale.ROOT);
        if (normalized.endsWith("-button") || normalized.equals("button")) return "button";
        if (normalized.contains("edit-box")) return "edit-box";
        if (normalized.contains("checkbox")) return "checkbox";
        if (listener instanceof ContainerEventHandler) return "container";
        return normalized.isBlank() ? "listener" : normalized;
    }

    private static final class Counter { int value; }
}