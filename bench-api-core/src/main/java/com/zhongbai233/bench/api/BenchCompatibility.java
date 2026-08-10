package com.zhongbai233.bench.api;

/** Declares the Core API range understood by a provider. */
public record BenchCompatibility(int apiMajor, int minimumApiMinor, int maximumApiMinor) {
    public BenchCompatibility {
        if (apiMajor < 0 || minimumApiMinor < 0 || maximumApiMinor < minimumApiMinor) {
            throw new IllegalArgumentException("Invalid API compatibility range");
        }
    }

    public boolean supports(int major, int minor) {
        return apiMajor == major && minor >= minimumApiMinor && minor <= maximumApiMinor;
    }

    public static BenchCompatibility exactly(int major, int minor) {
        return new BenchCompatibility(major, minor, minor);
    }
}