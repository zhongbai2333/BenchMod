# Minecraft 26.2 port

## Version line

- Minecraft 26.2, NeoForge 26.2.0.88, ModDevGradle 2.0.141, Java 25
- Gradle wrapper remains 9.5.1; initial independent-module tests ran with Gradle 9.5.0
- Local source version: `0.1.3-beta-mc26.2`
- Matching artifacts: `com.zhongbai233.bench:bench-api-neoforge-26.2:0.1.3-beta-mc26.2` and `com.zhongbai233.bench:bench-runtime-neoforge-26.2:0.1.3-beta-mc26.2`
- All common modules and the plugin use the same version. No immutable JitPack release has been created for this branch.

## Ported behavior

GUI/screen access moves to `Minecraft.gui`; HUD visibility uses `Hud`; screenshot targets and cameras use the 26.2 `GameRenderer` accessors; readiness obtains section counts from `LevelExtractor`. Flat/void world generation uses the registered flat world preset instead of the removed helper.

`modBench.clientGraphicsBackend` defaults to `opengl`; `-PmodBench.client.graphicsBackend=vulkan` overrides it. Both integrated and paired clients receive the official `--graphicsBackend` argument before device startup. Vanilla still attempts backend fallback, so reports include `clientGraphicsBackend` plus actual start/end backend, device, vendor and driver diagnostics. A backend mismatch invalidates the environment and prevents a PASSED comparable result.

## Verification (2026-10-03)

Passed:

- Direct Java 25 compilation of all Core/API/Runtime main sources against SHA1-verified official Minecraft 26.2 client and NeoForge 26.2.0.88 universal/dependencies
- Independent Gradle checks: Core API 20 tests, report schema 5, Gradle plugin 34, network core 4, network proxy 18; zero failures/errors/skips
- Plugin tests include backend default/DSL/CLI selection, invalid selection rejection, paired forwarding, ordinary-run/server isolation, configuration-cache reuse and invalidation
- `git diff --check`

Pending: wrapper root `check`, matching publication, independent example compile/isolation, dedicated-server smoke and OpenGL/Vulkan client execution. These are not claimed passed.

A normal source-based ModDev build reached NeoForm's decompiler but its external JVM exited unsuccessfully in this worker. The opt-in `-PmodBenchBinaryArtifacts=true` uses ModDev's public `enable { isDisableRecompilation = true }` path and official patched binaries without changing the ordinary source-based development default. It does not replace tests or use stubs. Verification is continuing on this path.
