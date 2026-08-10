package com.zhongbai233.bench.runtime.client;

import com.zhongbai233.bench.api.client.gui.BenchGuiRectangle;

/** Converts logical GUI rectangles to clamped framebuffer pixel rectangles by mapping all four edges. */
final class GuiCoordinateMapper {
    private GuiCoordinateMapper() {}

    static Mapping map(BenchGuiRectangle logical, int padding, int guiWidth, int guiHeight,
                       int framebufferWidth, int framebufferHeight) {
        if (padding < 0 || guiWidth < 1 || guiHeight < 1 || framebufferWidth < 1 || framebufferHeight < 1) {
            throw new IllegalArgumentException("Invalid GUI coordinate mapping inputs");
        }
        long expandedLeft = (long) logical.x() - padding;
        long expandedTop = (long) logical.y() - padding;
        long expandedRight = logical.right() + padding;
        long expandedBottom = logical.bottom() + padding;
        long clippedLeft = Math.max(0, expandedLeft);
        long clippedTop = Math.max(0, expandedTop);
        long clippedRight = Math.min(guiWidth, expandedRight);
        long clippedBottom = Math.min(guiHeight, expandedBottom);
        if (clippedRight <= clippedLeft || clippedBottom <= clippedTop) {
            throw new IllegalArgumentException("GUI bounds do not intersect the viewport");
        }
        BenchGuiRectangle clipped = new BenchGuiRectangle(Math.toIntExact(clippedLeft), Math.toIntExact(clippedTop),
            Math.toIntExact(clippedRight - clippedLeft), Math.toIntExact(clippedBottom - clippedTop));
        int left = floorScale(clipped.x(), framebufferWidth, guiWidth);
        int top = floorScale(clipped.y(), framebufferHeight, guiHeight);
        int right = ceilScale(clipped.right(), framebufferWidth, guiWidth);
        int bottom = ceilScale(clipped.bottom(), framebufferHeight, guiHeight);
        BenchGuiRectangle framebuffer = new BenchGuiRectangle(left, top, right - left, bottom - top);
        boolean wasClipped = clippedLeft != expandedLeft || clippedTop != expandedTop
            || clippedRight != expandedRight || clippedBottom != expandedBottom;
        return new Mapping(logical, clipped, framebuffer, wasClipped);
    }

    private static int floorScale(long value, int target, int source) {
        return Math.toIntExact(Math.floorDiv(Math.multiplyExact(value, target), source));
    }

    private static int ceilScale(long value, int target, int source) {
        long product = Math.multiplyExact(value, target);
        return Math.toIntExact(Math.floorDiv(product + source - 1L, source));
    }

    record Mapping(BenchGuiRectangle logical, BenchGuiRectangle clippedLogical,
                   BenchGuiRectangle framebuffer, boolean clipped) {}
}