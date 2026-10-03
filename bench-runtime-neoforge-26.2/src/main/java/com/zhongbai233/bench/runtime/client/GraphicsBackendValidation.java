package com.zhongbai233.bench.runtime.client;

/** Keeps fallback rendering from being reported as a comparable requested-backend run. */
final class GraphicsBackendValidation {
    private GraphicsBackendValidation() {}

    static String mismatch(String requested, String actual) {
        if (requested.equalsIgnoreCase(actual)) return "";
        return "client.graphics.backend_mismatch=requested:" + requested + ",actual:" + actual;
    }
}
