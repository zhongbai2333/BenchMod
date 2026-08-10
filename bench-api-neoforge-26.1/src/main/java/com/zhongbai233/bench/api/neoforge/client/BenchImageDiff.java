package com.zhongbai233.bench.api.neoforge.client;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/**
 * Pixel-level comparison of two screenshots.
 *
 * <p>A pixel counts as differing when any RGB channel deviates by more than
 * {@link #CHANNEL_TOLERANCE}, which absorbs codec and driver rounding without hiding real changes.
 * Scenarios use this to assert that a camera actually moved between captures, or that two captures
 * of the same pose stayed visually stable.
 *
 * @param width           compared image width in pixels
 * @param height          compared image height in pixels
 * @param differingPixels pixels whose channel delta exceeded the tolerance
 * @param totalPixels     all compared pixels ({@code width * height})
 * @param meanAbsoluteError mean absolute RGB channel delta over every pixel, in {@code [0, 255]}
 */
public record BenchImageDiff(
        int width, int height, long differingPixels, long totalPixels, double meanAbsoluteError) {
    /** Per-channel delta a pixel may show before it counts as differing. */
    public static final int CHANNEL_TOLERANCE = 4;

    public BenchImageDiff {
        if (width < 1 || height < 1 || totalPixels != (long) width * height
                || differingPixels < 0 || differingPixels > totalPixels || meanAbsoluteError < 0) {
            throw new IllegalArgumentException("Inconsistent image diff");
        }
    }

    /** Fraction of pixels that differ, in {@code [0, 1]}. */
    public double differingRatio() {
        return (double) differingPixels / totalPixels;
    }

    /** Compares two PNG files of identical dimensions. */
    public static BenchImageDiff compare(Path first, Path second) throws IOException {
        BufferedImage a = read(first);
        BufferedImage b = read(second);
        if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) {
            throw new IllegalArgumentException("Screenshot dimensions differ: "
                    + a.getWidth() + "x" + a.getHeight() + " vs " + b.getWidth() + "x" + b.getHeight());
        }
        int width = a.getWidth();
        int height = a.getHeight();
        int[] pixelsA = a.getRGB(0, 0, width, height, null, 0, width);
        int[] pixelsB = b.getRGB(0, 0, width, height, null, 0, width);
        long differing = 0;
        long totalDelta = 0;
        for (int i = 0; i < pixelsA.length; i++) {
            int rgbA = pixelsA[i];
            int rgbB = pixelsB[i];
            int deltaR = Math.abs(((rgbA >> 16) & 0xFF) - ((rgbB >> 16) & 0xFF));
            int deltaG = Math.abs(((rgbA >> 8) & 0xFF) - ((rgbB >> 8) & 0xFF));
            int deltaB = Math.abs((rgbA & 0xFF) - (rgbB & 0xFF));
            totalDelta += deltaR + deltaG + deltaB;
            if (deltaR > CHANNEL_TOLERANCE || deltaG > CHANNEL_TOLERANCE || deltaB > CHANNEL_TOLERANCE) {
                differing++;
            }
        }
        return new BenchImageDiff(width, height, differing, pixelsA.length,
                totalDelta / (pixelsA.length * 3.0));
    }

    private static BufferedImage read(Path file) throws IOException {
        BufferedImage image = ImageIO.read(file.toFile());
        if (image == null) throw new IOException("Not a readable image: " + file);
        return image;
    }
}
