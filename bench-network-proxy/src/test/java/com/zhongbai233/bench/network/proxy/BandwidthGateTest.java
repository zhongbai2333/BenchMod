package com.zhongbai233.bench.network.proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BandwidthGateTest {
    @Test
    void serializesReservationsAgainstVirtualFutureTime() {
        BandwidthGate gate = new BandwidthGate(64, 8, 1_000);
        assertEquals(1_000, gate.reserve(8, 1_000));
        assertEquals(1_000_001_000, gate.reserve(8, 1_000));
        assertEquals(2_000_001_000, gate.reserve(8, 1_000));
    }

    @Test
    void zeroBandwidthMeansUnlimitedAndDoesNotAlterDeadline() {
        BandwidthGate gate = new BandwidthGate(0, 0, 10);
        assertEquals(42, gate.reserve(Integer.MAX_VALUE, 42));
    }
}