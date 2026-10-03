package com.zhongbai233.bench.api.graphics;

import java.nio.ByteBuffer;
import java.util.Objects;

/** CPU reference pixels, deliberately independent of Minecraft, image codecs and GPU backends. */
public final class GraphicsMigrationFixtures {
    public static final long DEFAULT_SEED = 602263L;
    private GraphicsMigrationFixtures() {}

    /** Asymmetric color bars, alpha steps and row markers expose swizzles, flips and stale uploads. */
    public static byte[] rgba(int width, int height, long seed) {
        if (width < 1 || height < 1) throw new IllegalArgumentException("Positive dimensions required");
        byte[] result = new byte[Math.multiplyExact(Math.multiplyExact(width, height), 4)];
        int[][] bars = {{255, 0, 0}, {0, 255, 0}, {0, 0, 255}, {255, 255, 0}, {0, 255, 255}, {255, 0, 255}};
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            int i = (y * width + x) * 4;
            int[] bar = bars[x * bars.length / width];
            result[i] = (byte) (bar[0] ^ ((y * 23 + seed) & 63));
            result[i + 1] = (byte) (bar[1] ^ ((y * 47 + (seed >>> 6)) & 63));
            result[i + 2] = (byte) (bar[2] ^ ((x * 7 + y * 11 + (seed >>> 12)) & 63));
            result[i + 3] = (byte) ((x * 29 + y * 53 + seed) & 255);
        }
        return result;
    }

    /** Interleaved two-channel chroma-like fixture; U and V are intentionally unequal. */
    public static byte[] rg8(int width, int height, long seed) {
        byte[] rgba = rgba(width, height, seed);
        byte[] result = new byte[Math.multiplyExact(Math.multiplyExact(width, height), 2)];
        for (int p = 0; p < width * height; p++) {
            result[p * 2] = rgba[p * 4]; result[p * 2 + 1] = rgba[p * 4 + 1];
        }
        return result;
    }

    public static byte[] expected(GraphicsMigrationScene scene, long seed) {
        byte[] result = rgba(scene.width(), scene.height(), seed);
        if (scene == GraphicsMigrationScene.RG8_UV_CHANNELS) {
            for (int p = 0; p < result.length; p += 4) { result[p + 2] = 0; result[p + 3] = (byte) 255; }
        } else if (scene == GraphicsMigrationScene.SHADER_DEPTH_BLEND) {
            for (int p = 0; p < result.length; p += 4) {
                result[p] = (byte) 128; result[p + 1] = 0; result[p + 2] = 127; result[p + 3] = (byte) 255;
            }
        }
        return result;
    }

    /**
     * Packs decoder-style padded rows from the buffer's current position without changing it.
     * flipY describes the SOURCE row convention; no driver-specific flip is applied to readback.
     */
    public static byte[] packRows(ByteBuffer source, int width, int height, int channels, int rowStride, boolean flipY) {
        Objects.requireNonNull(source, "source");
        if (width < 1 || height < 1 || channels < 1 || channels > 4) throw new IllegalArgumentException("Invalid image dimensions/channels");
        int rowBytes = Math.multiplyExact(width, channels);
        if (rowStride < rowBytes) throw new IllegalArgumentException("Row stride is smaller than the pixel row");
        int required = Math.addExact(Math.multiplyExact(height - 1, rowStride), rowBytes);
        if (source.remaining() < required) throw new IllegalArgumentException("Truncated image rows");
        byte[] result = new byte[Math.multiplyExact(rowBytes, height)];
        for (int y = 0; y < height; y++) {
            int sourceRow = flipY ? height - 1 - y : y;
            source.get(source.position() + sourceRow * rowStride, result, y * rowBytes, rowBytes);
        }
        return result;
    }
}
