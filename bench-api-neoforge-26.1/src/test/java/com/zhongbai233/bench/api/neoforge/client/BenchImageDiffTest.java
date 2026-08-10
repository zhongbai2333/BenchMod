package com.zhongbai233.bench.api.neoforge.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BenchImageDiffTest {
    @TempDir Path directory;

    @Test
    void identicalImagesShowNoDifference() throws Exception {
        Path image = solidImage("a.png", 0x336699, 8, 8);

        BenchImageDiff diff = BenchImageDiff.compare(image, image);

        assertEquals(0, diff.differingPixels());
        assertEquals(0.0, diff.differingRatio());
        assertEquals(0.0, diff.meanAbsoluteError());
        assertEquals(64, diff.totalPixels());
    }

    @Test
    void toleranceAbsorbsCodecNoiseButNotRealChanges() throws Exception {
        Path base = solidImage("base.png", rgb(100, 100, 100), 4, 4);
        Path noisy = solidImage("noisy.png", rgb(103, 100, 98), 4, 4);
        Path changed = solidImage("changed.png", rgb(140, 100, 100), 4, 4);

        assertEquals(0.0, BenchImageDiff.compare(base, noisy).differingRatio());
        assertEquals(1.0, BenchImageDiff.compare(base, changed).differingRatio());
        assertEquals(40.0 / 3.0, BenchImageDiff.compare(base, changed).meanAbsoluteError(), 1.0E-9);
    }

    @Test
    void countsOnlyTheChangedRegion() throws Exception {
        BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        fill(image, 0x000000);
        Path base = write("region-base.png", image);
        for (int x = 0; x < 5; x++) image.setRGB(x, 0, 0xFFFFFF);
        Path modified = write("region-modified.png", image);

        BenchImageDiff diff = BenchImageDiff.compare(base, modified);

        assertEquals(5, diff.differingPixels());
        assertEquals(0.05, diff.differingRatio(), 1.0E-9);
        assertTrue(diff.meanAbsoluteError() > 0);
    }

    @Test
    void rejectsMismatchedDimensionsAndUnreadableFiles() throws Exception {
        Path small = solidImage("small.png", 0x112233, 4, 4);
        Path large = solidImage("large.png", 0x112233, 8, 4);
        Path bogus = directory.resolve("bogus.png");
        Files.writeString(bogus, "not a png");

        assertThrows(IllegalArgumentException.class, () -> BenchImageDiff.compare(small, large));
        assertThrows(IOException.class, () -> BenchImageDiff.compare(small, bogus));
    }

    private static int rgb(int r, int g, int b) {
        return (r << 16) | (g << 8) | b;
    }

    private Path solidImage(String name, int rgb, int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        fill(image, rgb);
        return write(name, image);
    }

    private static void fill(BufferedImage image, int rgb) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) image.setRGB(x, y, rgb);
        }
    }

    private Path write(String name, BufferedImage image) throws IOException {
        Path file = directory.resolve(name);
        ImageIO.write(image, "png", file.toFile());
        return file;
    }
}
