package com.zhongbai233.bench.api.client.gui;

import java.util.List;

/** Strict selector outcome; ambiguous matches are never silently reduced to the first node. */
public record BenchGuiSelection(Status status, BenchGuiNode node, int matchCount, List<String> candidatePaths) {
    public enum Status { MATCHED, NOT_FOUND, AMBIGUOUS, INDEX_OUT_OF_RANGE }

    public BenchGuiSelection {
        candidatePaths = List.copyOf(candidatePaths);
        if (matchCount < 0) throw new IllegalArgumentException("matchCount must not be negative");
        if ((status == Status.MATCHED) != (node != null)) {
            throw new IllegalArgumentException("Exactly MATCHED selections must carry a node");
        }
    }

    public BenchGuiNode requireMatch() {
        if (status != Status.MATCHED) {
            throw new IllegalStateException("GUI selector result is " + status + "; candidates=" + candidatePaths);
        }
        return node;
    }
}