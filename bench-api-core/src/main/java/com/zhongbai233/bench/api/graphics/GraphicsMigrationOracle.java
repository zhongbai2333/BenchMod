package com.zhongbai233.bench.api.graphics;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Exact dimensions plus a bounded per-channel UNORM tolerance; every RGBA pixel is checked. */
public final class GraphicsMigrationOracle {
    private GraphicsMigrationOracle() {}
    public record Difference(int badPixels, int pixelCount, int maxChannelError, double meanAbsoluteError,
                             int firstBadPixel) {
        public boolean passed() { return badPixels == 0; }
        public double badPixelRatio() { return (double) badPixels / pixelCount; }
    }
    public static Difference compare(byte[] expected, byte[] actual, int tolerance) {
        if (expected.length == 0 || expected.length % 4 != 0 || actual.length != expected.length)
            throw new IllegalArgumentException("Expected equal nonempty RGBA image sizes");
        if (tolerance < 0 || tolerance > 255) throw new IllegalArgumentException("Invalid channel tolerance");
        int bad = 0, max = 0, first = -1; long total = 0;
        for (int p = 0; p < actual.length; p += 4) {
            boolean mismatch = false;
            for (int c = 0; c < 4; c++) {
                int error = Math.abs(Byte.toUnsignedInt(expected[p + c]) - Byte.toUnsignedInt(actual[p + c]));
                max = Math.max(max, error); total += error; mismatch |= error > tolerance;
            }
            if (mismatch) { bad++; if (first == -1) first = p / 4; }
        }
        return new Difference(bad, actual.length / 4, max, (double) total / actual.length, first);
    }
    public static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
