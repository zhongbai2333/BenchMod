package com.zhongbai233.bench.runtime;

import java.util.Arrays;
import java.util.List;

/**
 * Selects which registered scenarios actually run.
 *
 * <p>The expression is a comma-separated list of scenario ids; a trailing {@code *} matches by
 * prefix. A blank expression accepts everything. Non-matching scenarios are reported as
 * {@code SKIPPED}, and a restricted filter that matches nothing fails the run instead of
 * producing an empty "success".
 */
public final class ScenarioFilter {
    private static final ScenarioFilter UNRESTRICTED = new ScenarioFilter(List.of());

    private final List<String> patterns;

    private ScenarioFilter(List<String> patterns) {
        this.patterns = patterns;
    }

    public static ScenarioFilter parse(String expression) {
        if (expression == null || expression.isBlank()) return UNRESTRICTED;
        List<String> patterns = Arrays.stream(expression.split(","))
                .map(value -> value.trim())
                .filter(pattern -> !pattern.isEmpty())
                .toList();
        return patterns.isEmpty() ? UNRESTRICTED : new ScenarioFilter(patterns);
    }

    public boolean isRestricted() {
        return !patterns.isEmpty();
    }

    public boolean matches(String scenarioId) {
        if (patterns.isEmpty()) return true;
        for (String pattern : patterns) {
            if (pattern.endsWith("*")) {
                if (scenarioId.startsWith(pattern.substring(0, pattern.length() - 1))) return true;
            } else if (scenarioId.equals(pattern)) {
                return true;
            }
        }
        return false;
    }
}
