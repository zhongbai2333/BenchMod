package com.zhongbai233.bench.api.client.gui;

import java.util.List;
import java.util.Objects;

/** Detached node in the Screen/GuiEventListener interaction tree. */
public record BenchGuiNode(
        String path,
        String role,
        String className,
        String semanticName,
        String text,
        BenchGuiRectangle bounds,
        boolean visible,
        boolean active,
        boolean focused,
        boolean hovered,
        int tabOrderGroup,
        List<BenchGuiNode> children) {
    public BenchGuiNode {
        if (path == null || path.isBlank()) throw new IllegalArgumentException("GUI node path must not be blank");
        if (role == null || role.isBlank()) throw new IllegalArgumentException("GUI node role must not be blank");
        if (className == null || className.isBlank()) {
            throw new IllegalArgumentException("GUI node className must not be blank");
        }
        semanticName = semanticName == null ? "" : semanticName;
        text = text == null ? "" : text;
        Objects.requireNonNull(bounds, "bounds");
        children = List.copyOf(children);
    }
}