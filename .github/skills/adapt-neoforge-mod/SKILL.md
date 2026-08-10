---
name: adapt-neoforge-mod
description: "Adapt a NeoForge mod project to ModBench. Use when integrating ModBench, adding src/bench providers, configuring the Gradle plugin, creating server benchmark scenarios, diagnosing Provider discovery, or verifying benchmark dependency and production JAR isolation."
argument-hint: "Describe the NeoForge mod, target mod ID, and benchmark workload"
---

# Adapt a NeoForge Mod to ModBench

Use this workflow to integrate a consumer NeoForge Mod without coupling its production code to the benchmark harness.

## Supported baseline

- Java 25
- Gradle 9.5.1
- ModDevGradle 2.x (currently verified with 2.0.141)
- Minecraft 26.1.2 / NeoForge 26.1.2.76
- Dedicated server and integrated Client MVP are automated; Client creates or reuses a configured world/dimension, applies a graphics baseline, controls absolute pose/look-at/framing and camera paths, enforces readiness/environment validity, captures PNG artifacts, samples frames, optionally records JFR, writes JSON/JSONL/Markdown, and exits; GUI automatic input, paired separate-client mode, perceptual image comparison, and GameTest are not implemented

If the consumer uses another Minecraft development line, do not silently reuse the 26.1 API or Runtime. Add or select matching `bench-api-neoforge-<line>` and `bench-runtime-neoforge-<line>` modules.

## Integration workflow

1. Inspect the consumer build before editing:
   - confirm Java and Gradle versions;
   - locate the ModDev `mods` declaration and target Mod ID;
   - determine whether the project declares one or multiple Mods;
   - inspect existing source sets, runs, repositories, publications, and configuration-cache settings.
2. Apply both `net.neoforged.moddev` and `com.zhongbai233.minecraft-bench`.
3. Configure `modBench.targetMod` explicitly for multi-Mod projects. Also set the expected Provider count, deterministic seed, and phase timeout.
4. Dependencies are automatic: the plugin injects the Core/NeoForge APIs into `benchImplementation` and the Runtime Mod into `benchRuntimeMod` at its own version (publish ModBench with `publishToMavenLocal` first; opt out via `modBench.automaticDependencies = false`). Follow `docs/consumer-quickstart.md` for the settings/repository setup.
5. Put Provider code under `src/bench/java` and its ServiceLoader descriptor under `src/bench/resources/META-INF/services/com.zhongbai233.bench.api.BenchProvider`.
6. Implement `BenchServerProvider` and register deterministic, tick-driven scenarios. Keep constructors public, no-arg, and lightweight. Record workload metrics through `context.metrics().record(descriptor, value)` — they land in the report's `scenarios[].metrics` with full distribution statistics, attributed to the phase that recorded them.
7. Run compile and isolation checks before starting Minecraft.
8. Run the generated dedicated-server benchmark and validate `summary.json`.
9. Confirm the production JAR, sources JAR, publications, ordinary `runtimeClasspath`, and ordinary game runs contain no Runtime or bench-only content.

For client rendering work, also implement `BenchClientProvider`. Use `context.frames()` for Runtime-owned frame interval statistics, `context.automation()` for pose, camera paths, HUD visibility and screenshots, `context.environment()` for render readiness and environment validity, and client ticks for lifecycle progress. Script continuous motion with `BenchCameraPath` keyframes and advance the returned `BenchCameraPlayback` once per client tick instead of moving the player by hand. Gate a scenario on `context.environment().readiness().ready()` before measuring, and leave `BenchCaptureOptions.defaults()` in place so captures wait for a settled picture. Screenshot futures must be polled without blocking the render/client thread. Do not treat frame interval as GPU frame time, and do not report a run as a comparable measurement when `context.environment().isValid()` is false — the Runtime already downgrades it to `INCONCLUSIVE`.

Use [consumer checklist](./references/consumer-checklist.md) for exact acceptance criteria and [provider template](./assets/BenchProviderTemplate.java.txt) as a starting point.

## Provider rules

- `src/bench` may depend on `src/main`; production code must never depend on bench code.
- Use target Mod public APIs to create real server-authoritative workload state.
- Never open threads, own the global lifecycle, write a parallel report format, or mutate Runtime internals.
- `stabilize`, `warmup`, and `measure` must return quickly with `CONTINUE` or `COMPLETE`; never block the server thread waiting for future ticks.
- Throw `AssertionError` from `verify` when workload correctness fails.
- Record identifiers required for precise teardown; teardown must tolerate partial setup and run at most once.
- Scenario and Provider IDs must be stable and globally unique.

## Gradle and ModDev boundaries

- Use only public ModDev DSL: `ModDevExtension`, `ModModel`, `RunModel`, `addModdingDependenciesTo`, `runs`, `loadedMods`, `sourceSet`, and `gameDirectory`.
- Do not create a replacement `JavaExec`, inspect generated args files, reflect internal models, or depend on ModDev internal task classes.
- Preserve configuration-cache compatibility: task actions use managed properties and must not capture `Project`, script objects, or task instances.
- Never make `benchRuntimeMod` extend ordinary `runtimeOnly`; attach it only to the bench runtime classpath.
- Use the Runtime Mod classloader for `ServiceLoader`; do not rely on the thread context classloader in FML.

## Required verification

Before reporting an adaptation complete, verify all of the following:

- bench code compiles against Minecraft, NeoForge, and target main output;
- Provider discovery count matches configuration;
- the scenario reaches setup, stabilize, warmup, measure, verify, and teardown;
- the server exits automatically;
- `summary.json` exists, passes the report schema, and has the expected status;
- a second identical Gradle invocation reuses the configuration cache;
- production artifacts contain no bench classes, service descriptors, metadata, or resources;
- ordinary `runtimeClasspath` contains no `bench-runtime-neoforge` artifact.

## Failure diagnosis order

1. Missing Provider: verify the ServiceLoader filename and contents, transformed `bench` source folders, target `ModModel.sourceSet(bench)`, and Runtime game classloader.
2. Mod development not enabled: defer ModDev integration until consumer configuration is complete; do not work around it with internal tasks.
3. Loader constraint violation: ensure API, Runtime, target main, and Provider are loaded through compatible FML game loaders.
4. Runtime leakage: inspect configuration inheritance and the ordinary `runtimeClasspath` dependency report.
5. No final report: inspect fixed `MODBENCH` markers, partial report, server log, crash report, and timeout diagnostics in that order.

## Output expected from the AI

Summarize:

- files changed in the consumer;
- target Mod and Provider/scenario IDs;
- dependency and production-artifact isolation evidence;
- dedicated-server and report-validation results;
- unsupported features or version mismatches that remain.