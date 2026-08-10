package com.zhongbai233.bench.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class NetworkProfileTest {
    @Test
    void streamProxyAcceptsDelayBandwidthPauseAndAbort() {
        NetworkProfile profile = new NetworkProfile(1, "laggy", 7,
                new NetworkDirectionProfile(50, 10, 1_000_000, 16_384, 1_048_576, 16_384),
                new NetworkDirectionProfile(100, 0, 0, 0, 1_048_576, 16_384),
                List.of(
                        new NetworkFault("stall", NetworkDirection.SERVER_TO_CLIENT,
                                NetworkFaultKind.FORWARDING_PAUSE, NetworkSemanticLayer.TCP_STREAM, 1_000, 500, 1),
                        new NetworkFault("disconnect", NetworkDirection.CLIENT_TO_SERVER,
                                NetworkFaultKind.CONNECTION_ABORT, NetworkSemanticLayer.TCP_STREAM, 5_000, 0, 1)));
        profile.requireSupportedBy(NetworkBackendCapabilities.tcpStreamProxy());
    }

    @Test
    void tcpStreamCannotPretendToDropOrReorderPackets() {
        assertThrows(IllegalArgumentException.class, () -> new NetworkFault(
                "fake-loss", NetworkDirection.CLIENT_TO_SERVER, NetworkFaultKind.LOSS,
                NetworkSemanticLayer.TCP_STREAM, 0, 1_000, 0.1));
        NetworkFault packetLoss = new NetworkFault(
                "real-loss", NetworkDirection.CLIENT_TO_SERVER, NetworkFaultKind.LOSS,
                NetworkSemanticLayer.IP_PACKET, 0, 1_000, 0.1);
        NetworkProfile profile = new NetworkProfile(1, "packet", 1,
                NetworkDirectionProfile.PASSTHROUGH, NetworkDirectionProfile.PASSTHROUGH, List.of(packetLoss));
        assertThrows(IllegalArgumentException.class,
                () -> profile.requireSupportedBy(NetworkBackendCapabilities.tcpStreamProxy()));
    }

    @Test
    void profileHashAndDerivedStreamsAreStableAndSeparated() {
        NetworkFault a = new NetworkFault("a", NetworkDirection.CLIENT_TO_SERVER,
                NetworkFaultKind.BLACKHOLE, NetworkSemanticLayer.TCP_STREAM, 100, 20, 1);
        NetworkFault b = new NetworkFault("b", NetworkDirection.SERVER_TO_CLIENT,
                NetworkFaultKind.CONNECTION_ABORT, NetworkSemanticLayer.TCP_STREAM, 200, 0, 1);
        NetworkProfile first = new NetworkProfile(1, "stable", 42,
                NetworkDirectionProfile.PASSTHROUGH, NetworkDirectionProfile.PASSTHROUGH, List.of(a, b));
        NetworkProfile reordered = new NetworkProfile(1, "stable", 42,
                NetworkDirectionProfile.PASSTHROUGH, NetworkDirectionProfile.PASSTHROUGH, List.of(b, a));
        assertEquals(first.sha256(), reordered.sha256());
        assertEquals(first.scheduledFaults(), reordered.scheduledFaults());
        assertEquals(NetworkSeedDerivation.derive(first, NetworkDirection.CLIENT_TO_SERVER, "MEASURE", "jitter"),
                NetworkSeedDerivation.derive(reordered, NetworkDirection.CLIENT_TO_SERVER, "MEASURE", "jitter"));
        assertNotEquals(NetworkSeedDerivation.derive(first, NetworkDirection.CLIENT_TO_SERVER, "MEASURE", "jitter"),
                NetworkSeedDerivation.derive(first, NetworkDirection.SERVER_TO_CLIENT, "MEASURE", "jitter"));
        assertNotEquals(NetworkSeedDerivation.derive(first, NetworkDirection.CLIENT_TO_SERVER, "WARMUP", "jitter"),
                NetworkSeedDerivation.derive(first, NetworkDirection.CLIENT_TO_SERVER, "MEASURE", "jitter"));
    }

    @Test
    void validatesBoundedBandwidthAndUniqueFaultIds() {
        assertThrows(IllegalArgumentException.class,
                () -> new NetworkDirectionProfile(0, 0, 1_000, 0, 1_024, 1_024));
        NetworkFault fault = new NetworkFault("same", NetworkDirection.CLIENT_TO_SERVER,
                NetworkFaultKind.BLACKHOLE, NetworkSemanticLayer.TCP_STREAM, 0, 10, 1);
        assertThrows(IllegalArgumentException.class, () -> new NetworkProfile(1, "duplicates", 1,
                NetworkDirectionProfile.PASSTHROUGH, NetworkDirectionProfile.PASSTHROUGH,
                List.of(fault, fault)));
        assertThrows(IllegalArgumentException.class, () -> new NetworkFault(
                "wrong-layer", NetworkDirection.CLIENT_TO_SERVER, NetworkFaultKind.CONNECTION_ABORT,
                NetworkSemanticLayer.APPLICATION_MESSAGE, 0, 0, 1));
    }
}