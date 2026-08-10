package com.zhongbai233.bench.network;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Derives independent deterministic random streams without depending on thread scheduling. */
public final class NetworkSeedDerivation {
    public static final String ALGORITHM = "sha256-first64-v1";

    private NetworkSeedDerivation() {}

    public static long derive(NetworkProfile profile, NetworkDirection direction, String phase, String eventId) {
        if (phase == null || phase.isBlank() || eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("phase and eventId must not be blank");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(ByteBuffer.allocate(Long.BYTES).putLong(profile.seed()).array());
            digest.update(profile.sha256().getBytes(StandardCharsets.US_ASCII));
            digest.update((byte) 0);
            digest.update(direction.name().getBytes(StandardCharsets.US_ASCII));
            digest.update((byte) 0);
            digest.update(phase.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(eventId.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.wrap(digest.digest()).getLong();
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}