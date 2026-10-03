package com.zhongbai233.bench.runtime.client;

import com.zhongbai233.bench.api.BenchPhase;
import com.zhongbai233.bench.api.BenchStatus;
import com.zhongbai233.bench.api.neoforge.client.BenchClientContext;
import com.zhongbai233.bench.api.neoforge.client.BenchClientScenario;
import com.zhongbai233.bench.api.neoforge.client.BenchClientStepResult;
import com.zhongbai233.bench.runtime.PhaseTimeline;
import java.util.List;
import java.util.Objects;

/** Client-tick-driven lifecycle. It never blocks the render or client thread. */
public final class ClientScenarioRunner {
    private final BenchClientContext context;
    private final BenchClientScenario scenario;
    private final long phaseTimeoutTicks;
    private final PhaseTimeline timeline = new PhaseTimeline();
    private BenchPhase phase = BenchPhase.SETUP;
    private long phaseStartedTick;
    private BenchStatus status;
    private String failure;
    private boolean teardownCalled;
    private boolean started;

    public ClientScenarioRunner(BenchClientContext context, BenchClientScenario scenario, long phaseTimeoutTicks) {
        this.context = Objects.requireNonNull(context, "context");
        this.scenario = Objects.requireNonNull(scenario, "scenario");
        if (phaseTimeoutTicks < 1) throw new IllegalArgumentException("phaseTimeoutTicks must be positive");
        this.phaseTimeoutTicks = phaseTimeoutTicks;
    }

    public void start() {
        if (started) throw new IllegalStateException("Runner has already started");
        started = true;
        phaseStartedTick = context.scheduler().currentTick();
        timeline.begin(BenchPhase.SETUP, phaseStartedTick);
        try {
            scenario.setup(context);
            advance(BenchPhase.STABILIZE);
        } catch (Exception | AssertionError exception) {
            fail(exception);
        }
    }

    public void tick() {
        if (status != null) return;
        if (context.cancellation().isCancelled()) {
            status = BenchStatus.ABORTED;
            failure = "Cancelled: " + context.cancellation().reason();
            timeline.endOpen(context.scheduler().currentTick(), PhaseTimeline.OUTCOME_ABORTED);
            teardown();
            return;
        }
        if (context.scheduler().currentTick() - phaseStartedTick >= phaseTimeoutTicks) {
            status = BenchStatus.TIMED_OUT;
            failure = "Phase " + phase + " exceeded " + phaseTimeoutTicks + " ticks";
            timeline.endOpen(context.scheduler().currentTick(), PhaseTimeline.OUTCOME_TIMED_OUT);
            teardown();
            return;
        }
        try {
            BenchClientStepResult result = switch (phase) {
                case STABILIZE -> scenario.stabilize(context);
                case WARMUP -> scenario.warmup(context);
                case MEASURE -> scenario.measure(context);
                default -> throw new IllegalStateException("Not a client-tick-driven phase: " + phase);
            };
            if (result == BenchClientStepResult.COMPLETE) advance(nextPhase(phase));
        } catch (Exception | AssertionError exception) {
            fail(exception);
        }
    }

    public BenchStatus status() { return status; }
    public BenchPhase phase() { return phase; }
    public String failure() { return failure; }

    /** Per-phase timing for the report, in the order the phases actually ran. */
    public List<PhaseTimeline.PhaseRecord> phaseRecords() {
        return timeline.records();
    }

    private void advance(BenchPhase next) {
        long tick = context.scheduler().currentTick();
        timeline.endOpen(tick, PhaseTimeline.OUTCOME_COMPLETED);
        phase = next;
        phaseStartedTick = tick;
        timeline.begin(next, tick);
        if (next == BenchPhase.VERIFY) {
            try {
                scenario.verify(context);
                status = BenchStatus.PASSED;
                timeline.endOpen(tick, PhaseTimeline.OUTCOME_COMPLETED);
            } catch (Exception | AssertionError exception) {
                fail(exception);
            } finally {
                teardown();
            }
        }
    }

    private void fail(Throwable exception) {
        status = status == null ? BenchStatus.FAILED : status;
        failure = exception.getClass().getName() + ": " + String.valueOf(exception.getMessage());
        timeline.endOpen(context.scheduler().currentTick(), PhaseTimeline.OUTCOME_FAILED);
        teardown();
    }

    private void teardown() {
        if (teardownCalled) return;
        teardownCalled = true;
        timeline.begin(BenchPhase.TEARDOWN, context.scheduler().currentTick());
        try {
            scenario.teardown(context);
            timeline.endOpen(context.scheduler().currentTick(), PhaseTimeline.OUTCOME_COMPLETED);
        } catch (Exception | AssertionError exception) {
            timeline.endOpen(context.scheduler().currentTick(), PhaseTimeline.OUTCOME_FAILED);
            if (failure == null) {
                status = BenchStatus.FAILED;
                failure = exception.getClass().getName() + ": " + String.valueOf(exception.getMessage());
            } else {
                failure += " | teardown: " + exception.getClass().getName() + ": "
                        + String.valueOf(exception.getMessage());
            }
        }
    }

    private static BenchPhase nextPhase(BenchPhase current) {
        return switch (current) {
            case STABILIZE -> BenchPhase.WARMUP;
            case WARMUP -> BenchPhase.MEASURE;
            case MEASURE -> BenchPhase.VERIFY;
            default -> throw new IllegalStateException("No next phase for " + current);
        };
    }
}