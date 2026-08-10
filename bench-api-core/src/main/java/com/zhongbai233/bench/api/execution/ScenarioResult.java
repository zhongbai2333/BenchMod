package com.zhongbai233.bench.api.execution;

import com.zhongbai233.bench.api.BenchStatus;
import java.util.List;
import java.util.Objects;

public record ScenarioResult(String scenarioId, BenchStatus status, List<PhaseEvent> phases, String failure) {
    public ScenarioResult {
        scenarioId = Objects.requireNonNull(scenarioId, "scenarioId");
        status = Objects.requireNonNull(status, "status");
        phases = List.copyOf(Objects.requireNonNull(phases, "phases"));
    }
}