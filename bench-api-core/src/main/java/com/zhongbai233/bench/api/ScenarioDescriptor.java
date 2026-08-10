package com.zhongbai233.bench.api;

import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Immutable metadata used to select and constrain a benchmark scenario. */
public record ScenarioDescriptor(String id, String displayName, Set<String> tags, Duration phaseTimeout) {
    private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9][a-z0-9._-]*");

    public ScenarioDescriptor {
        if (id == null || !ID_PATTERN.matcher(id).matches()) {
            throw new IllegalArgumentException("Scenario id must match " + ID_PATTERN);
        }
        displayName = Objects.requireNonNull(displayName, "displayName");
        tags = Set.copyOf(Objects.requireNonNull(tags, "tags"));
        phaseTimeout = Objects.requireNonNull(phaseTimeout, "phaseTimeout");
        if (displayName.isBlank() || phaseTimeout.isZero() || phaseTimeout.isNegative()) {
            throw new IllegalArgumentException("Display name and phase timeout must be non-empty and positive");
        }
    }
}