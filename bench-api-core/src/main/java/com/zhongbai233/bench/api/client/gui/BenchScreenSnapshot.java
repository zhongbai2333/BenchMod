package com.zhongbai233.bench.api.client.gui;

import java.util.List;

/** Detached snapshot of one Screen and its interaction tree. */
public record BenchScreenSnapshot(
        String screenClassName,
        String title,
        int guiWidth,
        int guiHeight,
        int framebufferWidth,
        int framebufferHeight,
        List<BenchGuiNode> roots,
        List<String> diagnostics) {
    public BenchScreenSnapshot {
        if (screenClassName == null || screenClassName.isBlank()) {
            throw new IllegalArgumentException("Screen class name must not be blank");
        }
        title = title == null ? "" : title;
        if (guiWidth < 1 || guiHeight < 1 || framebufferWidth < 1 || framebufferHeight < 1) {
            throw new IllegalArgumentException("Screen and framebuffer dimensions must be positive");
        }
        roots = List.copyOf(roots);
        diagnostics = List.copyOf(diagnostics);
    }

    public double scaleX() { return (double) framebufferWidth / guiWidth; }
    public double scaleY() { return (double) framebufferHeight / guiHeight; }

    public List<BenchGuiNode> flattened() {
        java.util.ArrayList<BenchGuiNode> result = new java.util.ArrayList<>();
        roots.forEach(node -> flatten(node, result));
        return List.copyOf(result);
    }

    private static void flatten(BenchGuiNode node, List<BenchGuiNode> target) {
        target.add(node);
        node.children().forEach(child -> flatten(child, target));
    }
}