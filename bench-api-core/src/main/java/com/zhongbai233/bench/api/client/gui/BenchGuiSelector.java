package com.zhongbai233.bench.api.client.gui;

/** Strongly typed selector for a detached GUI interaction node. Empty strings mean unrestricted. */
public record BenchGuiSelector(
        String semanticName,
        String role,
        String text,
        String className,
        boolean requireVisible,
        boolean requireActive,
        Integer nthMatch) {
    public BenchGuiSelector {
        semanticName = normalize(semanticName);
        role = normalize(role);
        text = normalize(text);
        className = normalize(className);
        if (semanticName.isEmpty() && role.isEmpty() && text.isEmpty() && className.isEmpty()) {
            throw new IllegalArgumentException("GUI selector must contain at least one identity constraint");
        }
        if (nthMatch != null && nthMatch < 0) throw new IllegalArgumentException("nthMatch must not be negative");
    }

    public static BenchGuiSelector semanticName(String semanticName) {
        return new BenchGuiSelector(semanticName, "", "", "", true, false, null);
    }

    public static BenchGuiSelector roleAndText(String role, String text) {
        return new BenchGuiSelector("", role, text, "", true, false, null);
    }

    public BenchGuiSelector requiringActive() {
        return new BenchGuiSelector(semanticName, role, text, className, requireVisible, true, nthMatch);
    }

    public BenchGuiSelector nth(int index) {
        return new BenchGuiSelector(semanticName, role, text, className, requireVisible, requireActive, index);
    }

    private static String normalize(String value) { return value == null ? "" : value.trim(); }
}