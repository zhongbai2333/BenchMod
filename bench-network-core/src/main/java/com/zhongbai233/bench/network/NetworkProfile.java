package com.zhongbai233.bench.network;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/** Canonical, direction-aware network impairment profile for one paired experiment. */
public record NetworkProfile(
        int profileVersion,
        String id,
        long seed,
        NetworkDirectionProfile clientToServer,
        NetworkDirectionProfile serverToClient,
        List<NetworkFault> faults) {
    public NetworkProfile {
        if (profileVersion < 1) throw new IllegalArgumentException("profileVersion must be positive");
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Profile id must not be blank");
        Objects.requireNonNull(clientToServer, "clientToServer");
        Objects.requireNonNull(serverToClient, "serverToClient");
        faults = List.copyOf(Objects.requireNonNull(faults, "faults"));
        HashSet<String> ids = new HashSet<>();
        for (NetworkFault fault : faults) {
            if (!ids.add(fault.id())) throw new IllegalArgumentException("Duplicate fault id: " + fault.id());
        }
    }

    /** Fails before process startup when a backend cannot honor every requested semantic. */
    public void requireSupportedBy(NetworkBackendCapabilities backend) {
        Objects.requireNonNull(backend, "backend");
        requireDirectionSupported(clientToServer, backend);
        requireDirectionSupported(serverToClient, backend);
        for (NetworkFault fault : faults) {
            if (!backend.capabilities().contains(fault.requiredCapability())) {
                throw new IllegalArgumentException("Backend " + backend.backendId() + " lacks capability "
                        + fault.requiredCapability() + " required by fault " + fault.id());
            }
        }
    }

    /** Stable hash used in participant and paired reports. */
    public String sha256() {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonicalForm().getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    /** Canonical execution order; profile list order never influences an impairment trajectory. */
    public List<NetworkFault> scheduledFaults() {
        return faults.stream().sorted(java.util.Comparator
                .comparingLong(NetworkFault::startOffsetMillis)
                .thenComparing(NetworkFault::id)).toList();
    }

    String canonicalForm() {
        StringBuilder value = new StringBuilder();
        value.append(profileVersion).append('\n').append(id).append('\n').append(seed).append('\n');
        appendDirection(value, clientToServer);
        appendDirection(value, serverToClient);
        scheduledFaults().forEach(fault -> value
                .append(fault.id()).append('|').append(fault.direction()).append('|').append(fault.kind()).append('|')
                .append(fault.semanticLayer()).append('|').append(fault.startOffsetMillis()).append('|')
                .append(fault.durationMillis()).append('|')
                .append(Long.toUnsignedString(Double.doubleToLongBits(fault.probability()))).append('\n'));
        return value.toString();
    }

    private static void requireDirectionSupported(
            NetworkDirectionProfile direction, NetworkBackendCapabilities backend) {
        if (direction.baseLatencyMillis() > 0 && !backend.capabilities().contains(NetworkCapability.FIXED_LATENCY)) {
            throw new IllegalArgumentException("Backend lacks fixed latency support");
        }
        if (direction.jitterMillis() > 0
                && !backend.capabilities().contains(NetworkCapability.DETERMINISTIC_JITTER)) {
            throw new IllegalArgumentException("Backend lacks deterministic jitter support");
        }
        if (direction.bandwidthBitsPerSecond() > 0
                && !backend.capabilities().contains(NetworkCapability.BANDWIDTH_LIMIT)) {
            throw new IllegalArgumentException("Backend lacks bandwidth limiting support");
        }
    }

    private static void appendDirection(StringBuilder value, NetworkDirectionProfile direction) {
        value.append(direction.baseLatencyMillis()).append('|').append(direction.jitterMillis()).append('|')
                .append(direction.bandwidthBitsPerSecond()).append('|').append(direction.burstBytes()).append('|')
            .append(direction.maxQueueBytes()).append('|').append(direction.streamQuantumBytes()).append('\n');
    }
}