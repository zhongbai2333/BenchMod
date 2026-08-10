package com.zhongbai233.bench.runtime.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class FrameIntervalSamplerTest {
    @Test
    void computesIntervalsAndPercentiles() {
        FrameIntervalSampler sampler = new FrameIntervalSampler(4);
        sampler.recordFrame(10);
        sampler.recordFrame(20);
        sampler.recordFrame(50);
        sampler.recordFrame(70);

        assertEquals(3, sampler.sampleCount());
        assertEquals(20, sampler.meanIntervalNanos());
        assertEquals(30, sampler.percentileIntervalNanos(95));
        assertEquals(30, sampler.maxIntervalNanos());
    }

    @Test
    void tracksDroppedSamplesWithoutGrowing() {
        FrameIntervalSampler sampler = new FrameIntervalSampler(1);
        sampler.recordFrame(1);
        sampler.recordFrame(2);
        sampler.recordFrame(3);

        assertEquals(1, sampler.sampleCount());
        assertEquals(1, sampler.droppedSampleCount());
        assertThrows(IllegalArgumentException.class, () -> sampler.percentileIntervalNanos(101));
    }
}