package com.zhongbai233.bench.api.client.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class BenchGuiSelectorsTest {
    @Test
    void semanticNameSurvivesTreeReorderingAndDefensiveCopies() {
        BenchGuiNode connect = node("screen/1", "button", "connect", "Connect", true, true);
        BenchGuiNode cancel = node("screen/0", "button", "cancel", "Cancel", true, true);
        var mutable = new java.util.ArrayList<>(List.of(cancel, connect));
        BenchScreenSnapshot snapshot = snapshot(mutable);
        mutable.clear();
        assertEquals(connect, BenchGuiSelectors.select(snapshot,
                BenchGuiSelector.semanticName("connect")).requireMatch());
    }

    @Test
    void duplicateTextIsAmbiguousUnlessNthIsExplicit() {
        BenchScreenSnapshot snapshot = snapshot(List.of(
                node("screen/0", "button", "", "Same", true, true),
                node("screen/1", "button", "", "Same", true, true)));
        BenchGuiSelector selector = BenchGuiSelector.roleAndText("button", "Same");
        BenchGuiSelection ambiguous = BenchGuiSelectors.select(snapshot, selector);
        assertEquals(BenchGuiSelection.Status.AMBIGUOUS, ambiguous.status());
        assertEquals(2, ambiguous.matchCount());
        assertThrows(IllegalStateException.class, ambiguous::requireMatch);
        assertEquals("screen/1", BenchGuiSelectors.select(snapshot, selector.nth(1)).requireMatch().path());
        assertEquals(BenchGuiSelection.Status.INDEX_OUT_OF_RANGE,
                BenchGuiSelectors.select(snapshot, selector.nth(2)).status());
    }

    @Test
    void visibilityAndActiveFiltersAreStrict() {
        BenchScreenSnapshot snapshot = snapshot(List.of(
                node("screen/0", "button", "hidden", "Hidden", false, true),
                node("screen/1", "button", "disabled", "Disabled", true, false)));
        assertEquals(BenchGuiSelection.Status.NOT_FOUND,
                BenchGuiSelectors.select(snapshot, BenchGuiSelector.semanticName("hidden")).status());
        assertEquals(BenchGuiSelection.Status.NOT_FOUND,
                BenchGuiSelectors.select(snapshot,
                        BenchGuiSelector.semanticName("disabled").requiringActive()).status());
        BenchGuiSelector allowHidden = new BenchGuiSelector("hidden", "", "", "", false, false, null);
        assertEquals("screen/0", BenchGuiSelectors.select(snapshot, allowHidden).requireMatch().path());
    }

    @Test
    void recursivelyMatchesNestedInteractionNodes() {
        BenchGuiNode nested = node("screen/0/children[0]", "button", "nested-action", "Run", true, true);
        BenchGuiNode container = new BenchGuiNode("screen/0", "container", "example.Container", "", "",
                new BenchGuiRectangle(0, 0, 100, 100), true, true, false, false, -1, List.of(nested));
        BenchScreenSnapshot snapshot = snapshot(List.of(container));
        assertEquals(nested, BenchGuiSelectors.select(snapshot,
                BenchGuiSelector.semanticName("nested-action")).requireMatch());
        assertEquals(List.of(container, nested), snapshot.flattened());
    }

    private static BenchScreenSnapshot snapshot(List<BenchGuiNode> roots) {
        return new BenchScreenSnapshot("example.Screen", "Title", 320, 180, 640, 360, roots, List.of());
    }

    private static BenchGuiNode node(String path, String role, String semanticName, String text,
                                     boolean visible, boolean active) {
        return new BenchGuiNode(path, role, "example.Widget", semanticName, text,
                new BenchGuiRectangle(1, 2, 30, 20), visible, active, false, false, 0, List.of());
    }
}