package com.zhongbai233.bench.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PairedParticipantLayoutTest {
    @Test
    void oneClientKeepsTheOriginalPublicDirectoryName() {
        assertEquals("remote-client", PairedParticipantLayout.remoteClientRunType(0, 1));
    }

    @Test
    void multipleClientsReceiveStableDistinctDirectoryNames() {
        assertEquals("remote-client-0", PairedParticipantLayout.remoteClientRunType(0, 2));
        assertEquals("remote-client-1", PairedParticipantLayout.remoteClientRunType(1, 2));
    }

    @Test
    void invalidParticipantCoordinatesFailBeforeLaunch() {
        assertThrows(IllegalArgumentException.class,
                () -> PairedParticipantLayout.remoteClientRunType(-1, 2));
        assertThrows(IllegalArgumentException.class,
                () -> PairedParticipantLayout.remoteClientRunType(2, 2));
        assertThrows(IllegalArgumentException.class,
                () -> PairedParticipantLayout.remoteClientRunType(0, 0));
    }
}
