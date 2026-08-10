plugins {
    java
    id("net.neoforged.moddev")
    id("com.zhongbai233.minecraft-bench")
}

group = providers.gradleProperty("mod_group_id").get()
version = providers.gradleProperty("mod_version").get()

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
    withSourcesJar()
}

neoForge {
    version = providers.gradleProperty("neo_version").get()
    mods {
        create("simplebench") {
            sourceSet(sourceSets.main.get())
        }
    }
}

// The ModBench plugin adds the API and Runtime dependencies automatically at its own version.

tasks.withType<JavaCompile>().configureEach {
    options.release = 25
    options.encoding = "UTF-8"
}

tasks.named<ProcessResources>("processResources") {
    val props = mapOf(
        "mod_id" to providers.gradleProperty("mod_id").get(),
        "mod_name" to providers.gradleProperty("mod_name").get(),
        "mod_version" to providers.gradleProperty("mod_version").get(),
        "neo_version" to providers.gradleProperty("neo_version").get(),
        "minecraft_version" to providers.gradleProperty("minecraft_version").get(),
    )
    inputs.properties(props)
    filesMatching("META-INF/neoforge.mods.toml") { expand(props) }
}

// Report verification, result cleaning, artifact collection, and the production JAR check are
// registered by the ModBench plugin; the example only supplies its scenario expectations.
tasks.named<com.zhongbai233.bench.gradle.VerifyBenchReportTask>("verifyBenchServerReport") {
    expectedScenarioId.set("simplebench.server-smoke")
    expectedArtifactPaths.set(
        listOf("artifacts/samples/simplebench.server-smoke.jsonl", "artifacts/jfr/recording.jfr")
    )
    expectedMetricNames.set(listOf("server.tick.duration", "simplebench.workload.loaded_entities"))
    expectedLoadedModIds.set(listOf("minecraft", "neoforge", "simplebench", "modbench_runtime"))
}

tasks.named<com.zhongbai233.bench.gradle.VerifyBenchReportTask>("verifyBenchClientReport") {
    expectedScenarioId.set("simplebench.client-render-smoke")
    expectedArtifactPaths.set(
        listOf(
            "artifacts/screenshots/simple-client-render-smoke.png",
            "artifacts/screenshots/simple-client-render-orbit.png",
            "artifacts/screenshots/simple-client-render-hud-free.png",
            "artifacts/samples/simplebench.client-render-smoke.jsonl",
            "artifacts/jfr/recording.jfr",
            "artifacts/custom/camera-diff.csv",
        )
    )
    expectedDiagnostics.set(
        listOf(
            "client.environment.render_ready_reached=true",
            "client.environment.valid=true",
            "client.screenshot.gate_satisfied=true",
            "client.screenshot.hud_hidden=true",
            "client.graphics.start.inactivity_fps_limit=minimized",
            "client.graphics.start.mouse_grabbed=false",
        )
    )
    expectedMetricNames.set(
        listOf("client.frame.interval", "simplebench.camera.path_ticks", "simplebench.screenshot.diff_ratio")
    )
    expectedLoadedModIds.set(listOf("minecraft", "neoforge", "simplebench", "modbench_runtime"))
}

val verifyBenchExample by tasks.registering {
    dependsOn(tasks.named("runBenchServer"), tasks.named("verifyBenchServerReport"))
}

val verifyBenchClientExample by tasks.registering {
    dependsOn(tasks.named("runBenchClient"), tasks.named("verifyBenchClientReport"))
}

modBench {
    targetMod = "simplebench"
    expectedProviderCount = 1
    seed = 7
    phaseTimeoutTicks = 600
    clientWorldId = "modbench-client-world"
    clientAutoWorld = true
    clientWindowWidth = 1280
    clientWindowHeight = 720
    clientVsync = false
    clientFpsLimit = 260
    clientRenderDistance = 12
    clientSimulationDistance = 12
    // The example runs on interactive developer machines where the game window can lose focus.
    // Keep the default `true` on a dedicated benchmark machine so a stolen focus reports INCONCLUSIVE.
    clientRequireWindowFocus = false
    // Low-overhead JFR profile recorded for every run, registered as artifacts/jfr/recording.jfr.
    jfrEnabled = true
    pairedServerScenarios = "simplebench.server-smoke"
    pairedClientScenarios = "simplebench.client-render-smoke"
    pairedStartupTimeoutSeconds = 90
    pairedClientTimeoutSeconds = 120
}