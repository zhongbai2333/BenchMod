package com.zhongbai233.bench.runtime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ScenarioFilterTest {
    @Test
    void blankExpressionAcceptsEverything() {
        for (String expression : new String[] {null, "", "  ", ","}) {
            ScenarioFilter filter = ScenarioFilter.parse(expression);
            assertFalse(filter.isRestricted());
            assertTrue(filter.matches("any.scenario"));
        }
    }

    @Test
    void exactIdsMatchOnlyThemselves() {
        ScenarioFilter filter = ScenarioFilter.parse("a.one, b.two");

        assertTrue(filter.isRestricted());
        assertTrue(filter.matches("a.one"));
        assertTrue(filter.matches("b.two"));
        assertFalse(filter.matches("a.one.extra"));
        assertFalse(filter.matches("c.three"));
    }

    @Test
    void trailingStarMatchesByPrefix() {
        ScenarioFilter filter = ScenarioFilter.parse("super_lead.rope-*");

        assertTrue(filter.matches("super_lead.rope-air-rest"));
        assertTrue(filter.matches("super_lead.rope-stack-contact"));
        assertFalse(filter.matches("super_lead.server-load"));
    }
}
