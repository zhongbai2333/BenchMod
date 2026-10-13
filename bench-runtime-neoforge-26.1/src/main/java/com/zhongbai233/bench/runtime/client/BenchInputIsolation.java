package com.zhongbai233.bench.runtime.client;

/** Early run marker, available before the first window/world/GUI transition or client tick. */
public final class BenchInputIsolation {
    private BenchInputIsolation() {}

    /** Ordinary game runs retain their original native input behavior. */
    public static boolean active() {
        String results = System.getProperty("modBench.resultDirectory");
        return results != null && !results.isBlank();
    }
}
