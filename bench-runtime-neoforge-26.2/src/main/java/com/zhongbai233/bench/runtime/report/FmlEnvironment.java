package com.zhongbai233.bench.runtime.report;

import java.util.Comparator;
import java.util.List;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;

/**
 * The FML-touching half of environment capture. Kept separate so unit tests can use
 * {@link EnvironmentInfo} without loading any mod-loader class.
 */
public final class FmlEnvironment {
    private FmlEnvironment() {}

    /** Best-effort capture; a partially initialized loader yields empty fields, never a crash. */
    public static EnvironmentInfo capture() {
        String minecraft = "";
        String neoForge = "";
        try {
            var versions = FMLLoader.getCurrent().getVersionInfo();
            minecraft = versions.mcVersion();
            neoForge = versions.neoForgeVersion();
        } catch (Throwable ignored) {
            // Version info stays empty when the loader is unavailable.
        }
        List<EnvironmentInfo.ModEntry> mods = List.of();
        try {
            mods = ModList.get().getMods().stream()
                    .map(mod -> new EnvironmentInfo.ModEntry(mod.getModId(), String.valueOf(mod.getVersion())))
                    .sorted(Comparator.comparing(mod -> mod.id()))
                    .toList();
        } catch (Throwable ignored) {
            // The mod list stays empty when FML has not finished loading.
        }
        return EnvironmentInfo.capture(minecraft, neoForge, mods);
    }
}
