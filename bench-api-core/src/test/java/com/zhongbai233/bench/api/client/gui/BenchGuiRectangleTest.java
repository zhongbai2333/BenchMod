package com.zhongbai233.bench.api.client.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BenchGuiRectangleTest {
    @Test
    void expandsIntersectsAndUsesHalfOpenBounds() {
        BenchGuiRectangle rectangle = new BenchGuiRectangle(10, 20, 30, 40);
        assertEquals(new BenchGuiRectangle(5, 15, 40, 50), rectangle.expanded(5));
        assertEquals(new BenchGuiRectangle(20, 30, 20, 30), rectangle.intersection(
                new BenchGuiRectangle(20, 30, 100, 100)).orElseThrow());
        assertTrue(rectangle.contains(10, 20));
        assertFalse(rectangle.contains(40, 60));
        assertTrue(rectangle.intersection(new BenchGuiRectangle(40, 20, 1, 1)).isEmpty());
    }

    @Test
    void rejectsNegativeSizesAndOverflowingExpansion() {
        assertThrows(IllegalArgumentException.class, () -> new BenchGuiRectangle(0, 0, -1, 1));
        assertThrows(ArithmeticException.class,
                () -> new BenchGuiRectangle(Integer.MIN_VALUE, 0, 1, 1).expanded(1));
    }
}