package com.zhongbai233.bench.runtime.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zhongbai233.bench.api.client.gui.BenchGuiRectangle;
import org.junit.jupiter.api.Test;

class GuiCoordinateMapperTest {
    @Test
    void mapsAllFourEdgesAtNonIntegerScaleWithoutLosingPixels() {
        GuiCoordinateMapper.Mapping mapping = GuiCoordinateMapper.map(
                new BenchGuiRectangle(1, 1, 2, 2), 0, 3, 3, 10, 10);
        assertEquals(new BenchGuiRectangle(3, 3, 7, 7), mapping.framebuffer());
        assertFalse(mapping.clipped());
    }

    @Test
    void clampsPaddingAtViewportEdgesAndReportsClipping() {
        GuiCoordinateMapper.Mapping mapping = GuiCoordinateMapper.map(
                new BenchGuiRectangle(0, 170, 20, 10), 8, 320, 180, 1280, 720);
        assertEquals(new BenchGuiRectangle(0, 162, 28, 18), mapping.clippedLogical());
        assertEquals(new BenchGuiRectangle(0, 648, 112, 72), mapping.framebuffer());
        assertTrue(mapping.clipped());
    }

    @Test
    void rejectsRegionsCompletelyOutsideTheViewport() {
        assertThrows(IllegalArgumentException.class, () -> GuiCoordinateMapper.map(
                new BenchGuiRectangle(500, 500, 20, 20), 0, 320, 180, 1280, 720));
    }

    @Test
    void clampsExtremeRepresentableLogicalBoundsBeforeNarrowing() {
        GuiCoordinateMapper.Mapping mapping = GuiCoordinateMapper.map(
                new BenchGuiRectangle(Integer.MIN_VALUE, 10, Integer.MAX_VALUE, 20),
                Integer.MAX_VALUE, 320, 180, 1280, 720);
        assertEquals(new BenchGuiRectangle(0, 0, 320, 180), mapping.clippedLogical());
        assertEquals(new BenchGuiRectangle(0, 0, 1280, 720), mapping.framebuffer());
        assertTrue(mapping.clipped());
    }
}