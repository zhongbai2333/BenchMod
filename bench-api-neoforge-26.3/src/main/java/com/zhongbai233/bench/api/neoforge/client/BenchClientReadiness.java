package com.zhongbai233.bench.api.neoforge.client;

import java.util.Objects;

/**
 * Snapshot of how settled the client render pipeline is.
 *
 * @param ready           {@code true} when the world is drawn unobstructed and nothing is still
 *                        loading, meshing, or uploading
 * @param pendingReason   the first unmet condition, or an empty string when ready
 * @param loadedChunks    chunks currently held by the client chunk cache
 * @param renderedSections visible sections that already have renderable geometry
 * @param totalSections   sections tracked by the current view area
 * @param meshQueueEmpty  {@code true} when no chunk section is queued for meshing or upload
 * @param resourcesReady  {@code true} when no resource reload overlay is active
 */
public record BenchClientReadiness(
        boolean ready,
        String pendingReason,
        int loadedChunks,
        int renderedSections,
        int totalSections,
        boolean meshQueueEmpty,
        boolean resourcesReady) {
    public BenchClientReadiness {
        Objects.requireNonNull(pendingReason, "pendingReason");
    }
}
