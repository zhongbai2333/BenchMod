package com.zhongbai233.bench.api.execution;

import com.zhongbai233.bench.api.BenchPhase;
import com.zhongbai233.bench.api.BenchScenario;
import com.zhongbai233.bench.api.BenchStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Pure-Java lifecycle prototype. NeoForge runtimes will replace direct calls with side-aware scheduling. */
public final class ScenarioExecutor {
    private final Clock clock;

    public ScenarioExecutor(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public ScenarioResult execute(String scenarioId, BenchScenario scenario) {
        Objects.requireNonNull(scenario, "scenario");
        List<PhaseEvent> events = new ArrayList<>();
        Throwable primaryFailure = null;

        for (BenchPhase phase : List.of(BenchPhase.SETUP, BenchPhase.STABILIZE, BenchPhase.WARMUP,
                BenchPhase.MEASURE, BenchPhase.VERIFY)) {
            Throwable failure = invoke(phase, scenario, events);
            if (failure != null) {
                primaryFailure = failure;
                break;
            }
        }

        Throwable teardownFailure = invoke(BenchPhase.TEARDOWN, scenario, events);
        if (primaryFailure == null) {
            primaryFailure = teardownFailure;
        } else if (teardownFailure != null) {
            primaryFailure.addSuppressed(teardownFailure);
        }

        return new ScenarioResult(
                scenarioId,
                primaryFailure == null ? BenchStatus.PASSED : BenchStatus.FAILED,
                events,
                primaryFailure == null ? null : describe(primaryFailure));
    }

    private Throwable invoke(BenchPhase phase, BenchScenario scenario, List<PhaseEvent> events) {
        Instant start = clock.instant();
        Throwable failure = null;
        try {
            switch (phase) {
                case SETUP -> scenario.setup();
                case STABILIZE -> scenario.stabilize();
                case WARMUP -> scenario.warmup();
                case MEASURE -> scenario.measure();
                case VERIFY -> scenario.verify();
                case TEARDOWN -> scenario.teardown();
                default -> throw new IllegalArgumentException("Unsupported scenario phase: " + phase);
            }
        } catch (Throwable throwable) {
            failure = throwable;
        }
        events.add(new PhaseEvent(phase, start, clock.instant(), failure == null ? null : describe(failure)));
        return failure;
    }

    private static String describe(Throwable throwable) {
        String message = throwable.getMessage();
        return throwable.getClass().getName() + (message == null ? "" : ": " + message);
    }
}