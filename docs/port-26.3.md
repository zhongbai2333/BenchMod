# Minecraft / NeoForge 26.3 port

## Version boundaries

- Minecraft 26.3, NeoForge 26.3.0.45-beta, Java 25
- Separate API/runtime adapter modules; 26.1 modules retain their original target
- Local Maven group `com.zhongbai233.bench`, version `0.1.3-beta-mc26.3`
- API: `bench-api-neoforge-26.3`; runtime: `bench-runtime-neoforge-26.3`
- Plugin automatically selects the adapter from the public ModDev `getVersion()` API
- `modBench.neoForgeLine` may be set explicitly, but it must match ModDev when automatic dependencies are enabled
- Unsupported development lines fail early; manual adapters remain possible with `automaticDependencies = false`

## Graphics backends

The new runtime keeps captures on Minecraft's backend-neutral screenshot path. GUI screens/overlays and HUD access use `Minecraft.gui`; render targets/camera use `GameRenderer`; readiness section counts use `LevelExtractor`; fullscreen/minimize tracking follows the SDL window API.

`modBench.clientGraphicsBackend = "opengl"` is the deterministic default. Set it to `"vulkan"`, or pass `-PmodBench.client.graphicsBackend=vulkan`, for integrated or paired clients. The command-line property takes precedence and is propagated to each physical client; servers and ordinary runs receive no graphics argument. The 26.1 adapter rejects Vulkan requests and retains its original launch arguments.

Reports record requested backend plus actual device/backend/vendor/driver. If Minecraft falls back to a different backend, the run becomes `INCONCLUSIVE` instead of claiming comparable Vulkan measurements.

GUI mouse buttons, keyboard scancodes, layout keycodes, and modifiers now use Minecraft 26.3's SDL constants (`InputConstants`). Primary mouse button is **1**, not the old GLFW value 0; default clicks, double-clicks, and drags use the correct constant. Explicit callers must migrate their input constants too. `pressKey(key, keycode, modifiers)` takes an SDL scancode first and its layout keycode second.

## Low-memory development

`-PmodBenchBinaryArtifacts=true` opts into ModDev's supported binary-patching artifact path. It uses the real pinned Minecraft/NeoForge binaries and avoids decompilation/recompilation on constrained machines. Default developer builds still use source generation; CI and JitPack select the official binary path for reproducible memory use across both supported lines. No replacement game classes or stubs are used.

## Verification

Verified 2026-10-03 with Java 25.0.3 and the repository Gradle 9.5.1 wrapper:

- `check verifyReleaseReadiness -PmodBenchBinaryArtifacts=true`: **passed**, 257 tests, zero failures/errors/skips; all seven public modules published to Maven Local
- Includes retained 26.1 API/runtime regressions, 26.3 API/runtime, 41 Gradle plugin tests, network modules, schemas, Javadoc, source JARs and plugin validation
- Configuration cache reused on the successful aggregate invocation
- Default source-mode 26.3 API/runtime checks: **passed** (17 API + 70 runtime tests)
- `:bench-runtime-neoforge-26.3:runBenchServer`: **passed**; one provider, all six phases completed, automatic shutdown, exact Minecraft/NeoForge target versions, final report validated against the Draft 2020-12 schema
- Independent 26.3 consumer verification is in progress and is not yet claimed passed

The cloud sandbox blocks FML's normal local JVM attach socket. Tests were run through FML's supported premain path: the unmodified `DevAgent.class` from each matching official loader (11.0.13 / 12.0.8), a local agent manifest, and test-only JVM arguments. No Minecraft/NeoForge classes were replaced and no tests were disabled. Normal development machines can use FML's ordinary self-attach path.

Source-mode 26.3 Minecraft generation and checks also passed with ModDev 2.0.148. The initial parallel decompiler run failed within the external NeoForm tool before any BenchMod Java compile; the official binary path above and serialized source generation avoid that resource failure.

Client/Vulkan behavior requires a graphics-capable Minecraft 26.3 client; a successful Java compile alone is not evidence of visual correctness or benchmark accuracy.
