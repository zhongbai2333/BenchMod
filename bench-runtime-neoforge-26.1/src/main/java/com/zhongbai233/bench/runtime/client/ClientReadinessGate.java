package com.zhongbai233.bench.runtime.client;

import com.zhongbai233.bench.api.neoforge.client.BenchClientReadiness;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;

/**
 * Decides when the render pipeline has settled enough for a comparable capture.
 *
 * <p>Ready means the world is on screen unobstructed: resources reloaded, no screen or overlay in
 * front of it, chunks loaded, and every queued chunk section already meshed and uploaded.
 */
final class ClientReadinessGate {
    BenchClientReadiness evaluate(Minecraft minecraft, boolean screenExpected) {
        if (minecraft.level == null || minecraft.player == null) {
            return pending("client_level_missing", 0, 0, 0, false, false);
        }
        boolean resourcesReady = minecraft.getOverlay() == null;
        int loadedChunks = minecraft.level.getChunkSource().getLoadedChunksCount();
        LevelRenderer renderer = minecraft.levelRenderer;
        SectionRenderDispatcher dispatcher = renderer.getSectionRenderDispatcher();
        boolean meshQueueEmpty = dispatcher != null && dispatcher.isQueueEmpty();
        int renderedSections = renderer.countRenderedSections();
        int totalSections = (int) renderer.getTotalSections();
        if (!resourcesReady) {
            return pending("resource_reload_in_progress", loadedChunks, renderedSections, totalSections, false, false);
        }
        if (minecraft.screen != null && !screenExpected) {
            // The terrain-loading screen draws over the world, so a capture would show the overlay.
            return pending("screen_open=" + minecraft.screen.getClass().getSimpleName(),
                    loadedChunks, renderedSections, totalSections, meshQueueEmpty, true);
        }
        if (loadedChunks <= 0) {
            return pending("no_loaded_chunks", loadedChunks, renderedSections, totalSections, meshQueueEmpty, true);
        }
        if (dispatcher == null) {
            return pending("section_dispatcher_missing", loadedChunks, renderedSections, totalSections, false, true);
        }
        if (!meshQueueEmpty) {
            return pending("chunk_mesh_queue_not_empty", loadedChunks, renderedSections, totalSections, false, true);
        }
        if (renderedSections <= 0) {
            return pending("no_rendered_sections", loadedChunks, renderedSections, totalSections, true, true);
        }
        return new BenchClientReadiness(
                true, "", loadedChunks, renderedSections, totalSections, true, true);
    }

    private static BenchClientReadiness pending(String reason, int loadedChunks, int renderedSections,
                                                int totalSections, boolean meshQueueEmpty, boolean resourcesReady) {
        return new BenchClientReadiness(
                false, reason, loadedChunks, renderedSections, totalSections, meshQueueEmpty, resourcesReady);
    }
}
