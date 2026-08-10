package com.zhongbai233.bench.runtime;

import com.zhongbai233.bench.api.BenchPhase;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Records when each lifecycle phase of one scenario started and ended, in ticks and wall time.
 *
 * <p>Exactly one phase can be open at a time. Closing is idempotent so failure paths may close
 * defensively without tracking whether a phase is still running.
 */
public final class PhaseTimeline {
    /** Terminal outcome of one phase. */
    public static final String OUTCOME_COMPLETED = "completed";
    public static final String OUTCOME_FAILED = "failed";
    public static final String OUTCOME_TIMED_OUT = "timed_out";
    public static final String OUTCOME_ABORTED = "aborted";

    private final LongSupplier nanoClock;
    private final List<PhaseRecord> records = new ArrayList<>();
    private BenchPhase openPhase;
    private long openStartTick;
    private long openStartNanos;

    public PhaseTimeline() {
        this(System::nanoTime);
    }

    PhaseTimeline(LongSupplier nanoClock) {
        this.nanoClock = Objects.requireNonNull(nanoClock, "nanoClock");
    }

    public void begin(BenchPhase phase, long tick) {
        Objects.requireNonNull(phase, "phase");
        if (openPhase != null) {
            throw new IllegalStateException("Phase " + openPhase + " is still open");
        }
        openPhase = phase;
        openStartTick = tick;
        openStartNanos = nanoClock.getAsLong();
    }

    /** Closes the open phase with the given outcome; a no-op when nothing is open. */
    public void endOpen(long tick, String outcome) {
        Objects.requireNonNull(outcome, "outcome");
        if (openPhase == null) return;
        records.add(new PhaseRecord(openPhase, openStartTick, tick, openStartNanos, nanoClock.getAsLong(), outcome));
        openPhase = null;
    }

    public List<PhaseRecord> records() {
        return List.copyOf(records);
    }

    public record PhaseRecord(
            BenchPhase phase, long startTick, long endTick, long startNanos, long endNanos, String outcome) {
        public long durationTicks() {
            return endTick - startTick;
        }

        public long wallNanos() {
            return endNanos - startNanos;
        }
    }
}
