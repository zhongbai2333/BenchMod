# Minecraft 26.2 port

## Version line

- Minecraft 26.2, NeoForge 26.2.0.88, ModDevGradle 2.0.141, Java 25
- Gradle wrapper 9.5.1 verified; initial independent-module checks also ran with Gradle 9.5.0
- Local source version: `0.1.3-beta-mc26.2`
- Matching artifacts: `com.zhongbai233.bench:bench-api-neoforge-26.2:0.1.3-beta-mc26.2` and `com.zhongbai233.bench:bench-runtime-neoforge-26.2:0.1.3-beta-mc26.2`
- All common modules and the plugin use the same version. No immutable JitPack release has been created for this branch.

## Ported behavior

GUI/screen access moves to `Minecraft.gui`; HUD visibility uses `Hud`; screenshot targets and cameras use the 26.2 `GameRenderer` accessors; readiness obtains section counts from `LevelExtractor`. Flat/void world generation uses the registered flat world preset instead of the removed helper.

`modBench.clientGraphicsBackend` defaults to `opengl`; `-PmodBench.client.graphicsBackend=vulkan` overrides it. Both integrated and paired clients receive the official `--graphicsBackend` argument before device startup. Vanilla still attempts backend fallback, so reports include `clientGraphicsBackend` plus actual start/end backend, device, vendor and driver diagnostics. A backend mismatch invalidates the environment and prevents a PASSED comparable result.

## Verification (2026-10-03)

Passed:

- Direct Java 25 compilation of all Core/API/Runtime main sources against SHA1-verified official Minecraft 26.2 client and NeoForge 26.2.0.88 universal/dependencies
- Wrapper root `check verifyReleaseReadiness`: 167 tests (Core API 20, NeoForge API 17, report schema 5, Gradle plugin 34, Runtime 69, network core 4, network proxy 18); zero failures/errors/skips, matching Maven Local publications
- Identical repeated root invocation reuses the configuration cache
- Independent consumer `compileBenchJava check dependencies --configuration runtimeClasspath` passes; production JAR/source isolation task passes and ordinary runtime classpath contains no BenchMod dependencies
- Plugin tests include backend default/DSL/CLI selection, invalid selection rejection, paired forwarding, ordinary-run/server isolation, configuration-cache reuse and invalidation
- `git diff --check`

- Independent dedicated-server `runBenchServer verifyBenchServerReport` passes twice; the second identical invocation reuses the configuration cache
- Server report validates against schema 1.0.0, provider count is one, all six phases complete, measurement records 40 ticks and 40 provider metric samples, expected Minecraft/NeoForge/target/Runtime mods are loaded, and scenario JFR/sample artifacts exist
- Both server launches report `PASSED` and stop automatically; the repeat launch binds to loopback in the isolated test run directory

Not run: OpenGL/Vulkan graphical client execution and paired-client end-to-end runs. This worker has no display, Xvfb or Vulkan ICD installed; no graphical-backend performance result is claimed.

The normal source-based ModDev build reached NeoForm's decompiler but its external JVM exited unsuccessfully in this memory-constrained worker. GitHub CI independently reached upstream Minecraft source recompilation and failed in generated `net.minecraft.core.HolderSet$1.contents()`: the override has weaker access than public `HolderSet.Named.contents()` (run [37119517099](https://github.com/zhongbai2333/BenchMod/actions/runs/37119517099)). Both CI jobs therefore explicitly use the tested binary-artifact path. The opt-in `-PmodBenchBinaryArtifacts=true` uses ModDev's public `enable { isDisableRecompilation = true }` path and official patched binaries without changing the ordinary source-based development default. It does not replace tests or use stubs. All local root checks and publications passed on this path.

FML's dynamic self-attach failed in this worker because the JVM attach socket was unavailable. Verification used FML's supported startup instrumentation: the **unchanged official** `net/neoforged/fml/startup/DevAgent.class` from loader 11.0.16 was packaged with a `Premain-Class` manifest and passed with `-javaagent` to the test JVM via a local Gradle init script. This changes instrumentation delivery only; no tests, transforms or assertions were disabled. The development-run JVM uses the same startup agent through public `neoForge.runs.configureEach { jvmArguments.add(...) }`; Java 21 helper tools receive no Java 25 agent.


## Reproduce in a regular development environment

```sh
./gradlew -PmodBenchBinaryArtifacts=true check verifyReleaseReadiness
./gradlew -p examples/simple-neoforge-mod -PmodBenchLocal=true -PmodBenchBinaryArtifacts=true compileBenchJava check
./gradlew -p examples/simple-neoforge-mod -PmodBenchLocal=true -PmodBenchBinaryArtifacts=true runBenchServer verifyBenchServerReport
# On a machine with a display and the appropriate graphics driver:
./gradlew -p examples/simple-neoforge-mod -PmodBenchLocal=true -PmodBenchBinaryArtifacts=true -PmodBench.client.graphicsBackend=opengl runBenchClient verifyBenchClientReport
./gradlew -p examples/simple-neoforge-mod -PmodBenchLocal=true -PmodBenchBinaryArtifacts=true -PmodBench.client.graphicsBackend=vulkan runBenchClient verifyBenchClientReport
```

CI also explicitly uses binary artifacts and executes the independent dedicated-server smoke. The original source-recompilation failure remains an upstream limitation; binary-artifact success does not claim it has been fixed.


## Graphics migration assistant validation (2026-10-03)

The opt-in suite is documented in [graphics-migration-assistant.md](graphics-migration-assistant.md).
Validation after implementation:

- Root `check verifyReleaseReadiness`: 196 JUnit tests, zero failures/errors/skips; configuration cache reused
- 47 Python comparison tests; fixed contract, malformed evidence, scene/device/backend mismatches, vendor aliases and raw RGBA thresholds covered
- The actual Minecraft Vulkan GLSL frontend compiles all three probe shaders to intermediary SPIR-V and exposes the expected uniform/sampler bindings; the OpenGL ShaderC frontend also compiles all three. These are frontend checks, not device rendering
- Real FML public registration → Factory SPI → default scenario test with no device emits six BLOCKED results, explicit optional reload SKIP, and no synthetic pixel artifacts
- Independent graphics consumer `compileBenchJava check` plus ordinary `runtimeClasspath` inspection pass; production/source JAR isolation passes; identical invocation reuses configuration cache
- Original independent simple consumer still compiles and passes isolation; its real dedicated-server run and report verification pass after the new Runtime publication
- `git diff --check` passes

All Minecraft/GPU execution lives in the Runtime MOD's transformed classloader. The API provides only a pure-Java registration façade and its narrow Factory SPI, avoiding protected game-class loading from ordinary API libraries.

Still not run: graphical OpenGL/Vulkan clients, real pixel readbacks, GPU timing, or paired clients. No display, X11 socket, Xvfb, `/dev/dri`, or Vulkan ICD is present in this worker. Full engine resource reload is deliberately SKIP; close/recreate ownership checks are not a driver-global leak test.
