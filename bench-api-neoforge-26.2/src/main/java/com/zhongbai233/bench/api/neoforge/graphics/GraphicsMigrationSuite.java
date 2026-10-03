package com.zhongbai233.bench.api.neoforge.graphics;

import com.zhongbai233.bench.api.ScenarioDescriptor;
import com.zhongbai233.bench.api.neoforge.client.BenchClientRegistrar;
import com.zhongbai233.bench.api.neoforge.client.BenchClientScenarioFactory;
import java.time.Duration;
import java.util.ServiceLoader;
import java.util.Set;

/** Opt-in graphics probes. This API façade never loads Minecraft classes in a library classloader. */
public final class GraphicsMigrationSuite {
    public static final String SCENARIO_ID = "graphics-migration.suite";
    private GraphicsMigrationSuite() {}

    /** Runtime implementation SPI; applications normally only call register. */
    public interface Factory extends BenchClientScenarioFactory {}

    /** Register from a test-only client Provider. The registrar identifies the Runtime game loader. */
    public static void register(BenchClientRegistrar registrar) {
        var factories = ServiceLoader.load(Factory.class, registrar.getClass().getClassLoader()).stream().toList();
        if (factories.size() != 1) throw new IllegalStateException(
                "Expected exactly one matching ModBench graphics Runtime, found " + factories.size());
        registrar.register(new ScenarioDescriptor(SCENARIO_ID, "OpenGL to Blaze3D migration assistant",
                Set.of("graphics-migration", "client", "correctness"), Duration.ofSeconds(120)), factories.getFirst().get());
    }
}
