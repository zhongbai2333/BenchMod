package com.zhongbai233.bench.api.client.gui;

import java.util.List;

/** Pure selector matching over detached snapshots. */
public final class BenchGuiSelectors {
    private BenchGuiSelectors() {}

    public static BenchGuiSelection select(BenchScreenSnapshot snapshot, BenchGuiSelector selector) {
        List<BenchGuiNode> matches = snapshot.flattened().stream()
                .filter(node -> matches(node, selector)).toList();
        List<String> paths = matches.stream().map(BenchGuiNode::path).toList();
        if (selector.nthMatch() != null) {
            int index = selector.nthMatch();
            if (index >= matches.size()) {
                return new BenchGuiSelection(BenchGuiSelection.Status.INDEX_OUT_OF_RANGE, null,
                        matches.size(), paths);
            }
            return new BenchGuiSelection(BenchGuiSelection.Status.MATCHED, matches.get(index),
                    matches.size(), paths);
        }
        if (matches.isEmpty()) {
            return new BenchGuiSelection(BenchGuiSelection.Status.NOT_FOUND, null, 0, List.of());
        }
        if (matches.size() > 1) {
            return new BenchGuiSelection(BenchGuiSelection.Status.AMBIGUOUS, null, matches.size(), paths);
        }
        return new BenchGuiSelection(BenchGuiSelection.Status.MATCHED, matches.getFirst(), 1, paths);
    }

    private static boolean matches(BenchGuiNode node, BenchGuiSelector selector) {
        return (selector.semanticName().isEmpty() || selector.semanticName().equals(node.semanticName()))
                && (selector.role().isEmpty() || selector.role().equals(node.role()))
                && (selector.text().isEmpty() || selector.text().equals(node.text()))
                && (selector.className().isEmpty() || selector.className().equals(node.className()))
                && (!selector.requireVisible() || node.visible())
                && (!selector.requireActive() || node.active());
    }
}