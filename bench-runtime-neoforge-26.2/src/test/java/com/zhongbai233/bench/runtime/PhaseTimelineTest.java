package com.zhongbai233.bench.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zhongbai233.bench.api.BenchPhase;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class PhaseTimelineTest {
    @Test
    void recordsTickAndWallSpansPerPhase() {
        AtomicLong clock = new AtomicLong();
        PhaseTimeline timeline = new PhaseTimeline(() -> clock.addAndGet(10));

        timeline.begin(BenchPhase.SETUP, 1);
        timeline.endOpen(1, PhaseTimeline.OUTCOME_COMPLETED);
        timeline.begin(BenchPhase.STABILIZE, 1);
        timeline.endOpen(4, PhaseTimeline.OUTCOME_COMPLETED);

        List<PhaseTimeline.PhaseRecord> records = timeline.records();
        assertEquals(2, records.size());
        assertEquals(BenchPhase.SETUP, records.get(0).phase());
        assertEquals(0, records.get(0).durationTicks());
        assertEquals(10, records.get(0).wallNanos());
        assertEquals(BenchPhase.STABILIZE, records.get(1).phase());
        assertEquals(3, records.get(1).durationTicks());
        assertTrue(records.get(1).wallNanos() > 0);
    }

    @Test
    void closingWithNothingOpenIsANoOp() {
        PhaseTimeline timeline = new PhaseTimeline(() -> 0L);
        timeline.endOpen(5, PhaseTimeline.OUTCOME_FAILED);
        assertEquals(0, timeline.records().size());
    }

    @Test
    void doubleBeginIsAProgrammingError() {
        PhaseTimeline timeline = new PhaseTimeline(() -> 0L);
        timeline.begin(BenchPhase.SETUP, 1);
        assertThrows(IllegalStateException.class, () -> timeline.begin(BenchPhase.STABILIZE, 2));
    }

    @Test
    void failureOutcomesAreRecordedVerbatim() {
        PhaseTimeline timeline = new PhaseTimeline(() -> 0L);
        timeline.begin(BenchPhase.MEASURE, 10);
        timeline.endOpen(30, PhaseTimeline.OUTCOME_TIMED_OUT);

        assertEquals(PhaseTimeline.OUTCOME_TIMED_OUT, timeline.records().get(0).outcome());
        assertEquals(20, timeline.records().get(0).durationTicks());
    }
}
