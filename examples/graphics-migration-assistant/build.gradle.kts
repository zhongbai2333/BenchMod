plugins {
    java
    id("net.neoforged.moddev")
    id("com.zhongbai233.minecraft-bench")
}

group = providers.gradleProperty("mod_group_id").get()
version = providers.gradleProperty("mod_version").get()
java { toolchain.languageVersion = JavaLanguageVersion.of(25); withSourcesJar() }
neoForge {
    enable {
        version = providers.gradleProperty("neo_version").get()
        isDisableRecompilation = providers.gradleProperty("modBenchBinaryArtifacts").map(String::toBoolean).getOrElse(false)
    }
    mods { create("graphicsmigration") { sourceSet(sourceSets.main.get()) } }
}
tasks.withType<JavaCompile>().configureEach { options.release = 25; options.encoding = "UTF-8" }
tasks.named<ProcessResources>("processResources") {
    val props = listOf("mod_id", "mod_name", "mod_version", "neo_version", "minecraft_version")
        .associateWith { providers.gradleProperty(it).get() }
    inputs.properties(props)
    filesMatching("META-INF/neoforge.mods.toml") { expand(props) }
}
modBench {
    targetMod = "graphicsmigration"
    expectedProviderCount = 1
    seed = 602263
    phaseTimeoutTicks = 2400
    clientWorldId = "graphics-migration-world"
    clientWorldPreset = "void"
    clientWindowWidth = 640
    clientWindowHeight = 480
    clientRenderDistance = 2
    clientSimulationDistance = 2
    clientRequireWindowFocus = false
    clientVsync = false
    jfrEnabled = true
}
tasks.named<com.zhongbai233.bench.gradle.VerifyBenchReportTask>("verifyBenchClientReport") {
    expectedScenarioId.set("graphics-migration.suite")
    expectedArtifactPaths.set(listOf("artifacts/custom/graphics-migration.json"))
    expectedLoadedModIds.set(listOf("minecraft", "neoforge", "graphicsmigration", "modbench_runtime"))
    expectedDiagnostics.set(listOf("client.environment.valid=true"))
}
