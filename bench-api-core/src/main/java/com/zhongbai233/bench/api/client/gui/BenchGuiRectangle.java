package com.zhongbai233.bench.api.client.gui;

import java.util.Optional;

/** Immutable rectangle in logical GUI coordinates. */
public record BenchGuiRectangle(int x, int y, int width, int height) {
    public BenchGuiRectangle {
        if (width < 0 || height < 0) throw new IllegalArgumentException("GUI rectangle size must not be negative");
    }

    public long right() { return (long) x + width; }
    public long bottom() { return (long) y + height; }

    public boolean contains(int pointX, int pointY) {
        return pointX >= x && pointY >= y && pointX < right() && pointY < bottom();
    }

    public BenchGuiRectangle expanded(int padding) {
        if (padding < 0) throw new IllegalArgumentException("Padding must not be negative");
        long expandedX = (long) x - padding;
        long expandedY = (long) y - padding;
        long expandedWidth = (long) width + padding * 2L;
        long expandedHeight = (long) height + padding * 2L;
        return checked(expandedX, expandedY, expandedWidth, expandedHeight);
    }

    public Optional<BenchGuiRectangle> intersection(BenchGuiRectangle other) {
        long left = Math.max(x, other.x);
        long top = Math.max(y, other.y);
        long right = Math.min(right(), other.right());
        long bottom = Math.min(bottom(), other.bottom());
        if (right <= left || bottom <= top) return Optional.empty();
        return Optional.of(checked(left, top, right - left, bottom - top));
    }

    private static BenchGuiRectangle checked(long x, long y, long width, long height) {
        return new BenchGuiRectangle(Math.toIntExact(x), Math.toIntExact(y),
                Math.toIntExact(width), Math.toIntExact(height));
    }
}