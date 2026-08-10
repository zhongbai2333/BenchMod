package com.zhongbai233.bench.api.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BenchCameraFramerTest {
    @Test
    void widerTargetsMoveTheCameraBack() {
        var small = new BenchCameraFraming(new BenchBounds3(-1, -1, -1, 1, 1, 1), 0, 0, 1, 0.8);
        var wide = new BenchCameraFraming(new BenchBounds3(-8, -1, -1, 8, 1, 1), 0, 0, 1, 0.8);
        assertTrue(BenchCameraFramer.solve(wide, 70, 16.0 / 9.0).distance()
                > BenchCameraFramer.solve(small, 70, 16.0 / 9.0).distance());
    }

    @Test
    void translationPreservesDistanceAndMovesTheSolution() {
        var origin = new BenchCameraFraming(new BenchBounds3(-2, -1, -1, 2, 1, 1), 1, -0.3, 1, 0.75);
        var moved = new BenchCameraFraming(new BenchBounds3(8, 19, 29, 12, 21, 31), 1, -0.3, 1, 0.75);
        var first = BenchCameraFramer.solve(origin, 70, 16.0 / 9.0);
        var second = BenchCameraFramer.solve(moved, 70, 16.0 / 9.0);
        assertEquals(first.distance(), second.distance(), 1.0e-9);
        assertEquals(first.eyeX() + 10, second.eyeX(), 1.0e-9);
        assertEquals(first.eyeY() + 20, second.eyeY(), 1.0e-9);
        assertEquals(first.eyeZ() + 30, second.eyeZ(), 1.0e-9);
    }

    @Test
    void smallerFovMovesTheCameraBack() {
        var framing = BenchCameraFraming.threeQuarter(new BenchBounds3(0, 0, 0, 10, 3, 2), 0.8);
        assertTrue(BenchCameraFramer.solve(framing, 45, 16.0 / 9.0).distance()
                > BenchCameraFramer.solve(framing, 90, 16.0 / 9.0).distance());
    }

    @Test
    void everyCornerFitsInsideTheRequestedFrame() {
        double fov = 67.0;
        double aspect = 16.0 / 9.0;
        var bounds = new BenchBounds3(-7, 3, -2, 11, 8, 6);
        var framing = new BenchCameraFraming(bounds, 1, -0.4, 0.7, 0.78);
        var solution = BenchCameraFramer.solve(framing, fov, aspect);

        double fx = framing.directionX();
        double fy = framing.directionY();
        double fz = framing.directionZ();
        double rx = -fz;
        double ry = 0;
        double rz = fx;
        double rightLength = Math.sqrt(rx * rx + rz * rz);
        rx /= rightLength;
        rz /= rightLength;
        double ux = ry * fz - rz * fy;
        double uy = rz * fx - rx * fz;
        double uz = rx * fy - ry * fx;
        double verticalTangent = Math.tan(Math.toRadians(fov) * 0.5);
        double horizontalTangent = verticalTangent * aspect;

        for (int corner = 0; corner < 8; corner++) {
            double x = (corner & 1) == 0 ? bounds.minX() : bounds.maxX();
            double y = (corner & 2) == 0 ? bounds.minY() : bounds.maxY();
            double z = (corner & 4) == 0 ? bounds.minZ() : bounds.maxZ();
            double dx = x - solution.eyeX();
            double dy = y - solution.eyeY();
            double dz = z - solution.eyeZ();
            double depth = dx * fx + dy * fy + dz * fz;
            double projectedX = Math.abs(dx * rx + dy * ry + dz * rz) / (depth * horizontalTangent);
            double projectedY = Math.abs(dx * ux + dy * uy + dz * uz) / (depth * verticalTangent);
            assertTrue(depth > 0);
            assertTrue(projectedX <= framing.frameFill() + 1.0e-9);
            assertTrue(projectedY <= framing.frameFill() + 1.0e-9);
        }
    }

    @Test
    void rejectsInvalidInputs() {
        assertThrows(IllegalArgumentException.class,
                () -> new BenchBounds3(1, 0, 0, 0, 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new BenchCameraFraming(new BenchBounds3(0, 0, 0, 1, 1, 1), 0, 0, 0, 0.8));
        var framing = BenchCameraFraming.threeQuarter(new BenchBounds3(0, 0, 0, 1, 1, 1), 0.8);
        assertThrows(IllegalArgumentException.class, () -> BenchCameraFramer.solve(framing, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> BenchCameraFramer.solve(framing, 70, 0));
    }

    @Test
    void finiteExtremeInputsDoNotOverflowDuringNormalizationOrMidpoint() {
        var bounds = new BenchBounds3(Double.MAX_VALUE / 2, -1, -1, Double.MAX_VALUE, 1, 1);
        assertTrue(Double.isFinite(bounds.centerX()));
        var framing = new BenchCameraFraming(
                new BenchBounds3(-1, -1, -1, 1, 1, 1), Double.MAX_VALUE, Double.MAX_VALUE, 0, 0.8);
        assertTrue(Double.isFinite(framing.directionX()));
        assertTrue(Double.isFinite(framing.directionY()));
        assertTrue(BenchCameraFramer.solve(framing, 70, 16.0 / 9.0).distance() > 0);
    }
}