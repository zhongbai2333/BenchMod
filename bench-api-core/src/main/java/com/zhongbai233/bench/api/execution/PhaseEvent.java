package com.zhongbai233.bench.api.execution;

import com.zhongbai233.bench.api.BenchPhase;
import java.time.Instant;
import java.util.Objects;

public record PhaseEvent(BenchPhase phase, Instant startedAt, Instant endedAt, String failure) {
    public PhaseEvent {
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(startedAt, "startedAt");
        Objects.requireNonNull(endedAt, "endedAt");
        if (endedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("Phase end precedes its start");
        }
    }

    public boolean successful() {
        return failure == null;
    }
}