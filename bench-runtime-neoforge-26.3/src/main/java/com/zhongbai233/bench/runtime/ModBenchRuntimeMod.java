package com.zhongbai233.bench.runtime;

import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod(ModBenchRuntimeMod.MOD_ID)
public final class ModBenchRuntimeMod {
    public static final String MOD_ID = "modbench_runtime";

    public ModBenchRuntimeMod() {
        NeoForge.EVENT_BUS.register(new NeoForgeServerAdapter());
    }
}