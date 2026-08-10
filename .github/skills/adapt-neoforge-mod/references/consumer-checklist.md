# Consumer Adaptation Checklist

## Build prerequisites

- Java toolchain and Gradle daemon both use Java 25.
- The wrapper is used instead of a machine-global Gradle installation.
- ModDevGradle 2.x repositories are configured from public repository plugins or documented repositories.
- The target Minecraft line has matching ModBench API and Runtime artifacts.

## Consumer build

- Apply `java`, `net.neoforged.moddev`, and `com.zhongbai233.minecraft-bench`.
- Declare the production Mod normally in `neoForge.mods`.
- Configure `modBench.targetMod` when more than one Mod exists.
- Put Core and NeoForge API dependencies on `benchImplementation`.
- Put the Runtime artifact only on `benchRuntimeMod`.
- Do not add benchmark dependencies to production `implementation`, `runtimeOnly`, or publication variants.

## Provider source layout

- Java: `src/bench/java/<package>/<Provider>.java`
- Service descriptor: `src/bench/resources/META-INF/services/com.zhongbai233.bench.api.BenchProvider`
- The descriptor contains one Provider class name per line.
- Provider classes have a public no-argument constructor.
- Provider IDs and scenario IDs are stable and unique.

## Scenario behavior

- Setup creates target Mod state through supported public APIs.
- Stabilization waits across ticks instead of blocking.
- Warmup runs representative workload without recording final assertions.
- Measurement performs real workload and records only declared metrics.
- Verification proves the workload ran correctly.
- Teardown removes exactly the state created by the scenario and handles partial setup.
- Every phase has a deterministic completion condition and bounded timeout.

## Isolation acceptance

- `main` cannot compile against bench classes.
- Production JAR and sources JAR contain no Provider, service descriptor, or bench-only resources.
- Maven publications expose production variants only.
- Ordinary `runtimeClasspath` and ordinary runs do not include the Runtime Mod.
- Bench run loads target main, Provider, and Runtime through FML's game classloader.

## Runtime acceptance

- Logs contain `MODBENCH event=run_start` and `MODBENCH event=run_complete`.
- Discovered Provider count equals `expectedProviderCount`.
- Every required phase finishes or reports an explicit failure/timeout.
- Partial report is written during execution.
- Final `summary.json` validates against the supported schema.
- The server exits without player input.
- Failure runs preserve logs, partial report, crash reports, and diagnostics when available.
- `scenarios[].phases` records per-phase start/end ticks, wall nanos, and outcome; `scenarios[].metrics` records built-in tick/frame metrics and Provider metrics recorded via `context.metrics().record(...)` with count/min/max/mean/median/P90/P95/P99/stdDev.
- `environment` includes OS/CPU/memory, Java and redacted JVM args, Minecraft/NeoForge versions, and the full loadedMods list; `run.parameters` records timeouts and the client graphics baseline.
- `VerifyBenchReportTask` accepts `expectedMetricNames` and `expectedLoadedModIds` to gate on real measurements.
- The plugin auto-registers `verifyProductionJarHasNoBenchContent` (wired into `check`), `verifyBenchServerReport`/`verifyBenchClientReport` (configure expectations on these instead of registering your own), `cleanBench*Results` (runs before each `runBench*`), and `collectBench*Artifacts` (finalizes each run, bundling report + logs + crash-reports with a manifest even on failure).
- A TIMED_OUT scenario writes a full thread dump to `artifacts/thread-dumps/<scenario>.txt` and registers it as a report artifact.
- Raw metric samples are exported per scenario to `artifacts/samples/<scenario>.jsonl`; a human-readable `report.md` is written next to `summary.json` (JSON stays authoritative).
- `environment.git` carries the local commit and dirty flag; `clientStableFrameRatio` and `clientCaptureGateFrameBudget` tune the capture gates and are recorded in `run.parameters`.

## Client MVP

- `runBenchClient` is generated for the bench source set.
- A Provider may implement both `BenchServerProvider` and `BenchClientProvider`.
- Runtime automatically creates or reuses the configured integrated world; client scenarios start after `Minecraft.level` and `Minecraft.player` are available.
- `clientWorldPreset` picks overworld generation (`normal`/`flat`/`void`); non-default presets use suffixed world directories so switching never reuses stale generation. `clientDimension` (`overworld`/`the_nether`/`the_end`) teleports the player server-side before scenarios start; readiness gates re-arm in the target dimension.
- `prepareBenchServerWorld` pins the dedicated server's `level-seed` to `modBench.seed` and applies `serverLevelType`/`serverGeneratorSettings`, resetting the world when provisioning changes.
- Runtime samples intervals at `RenderFrameEvent.Pre` into a fixed-capacity buffer.
- Client scenarios use `context.automation()` for absolute pose, look-at, stationary input/velocity, HUD visibility, and PNG capture.
- Continuous camera motion uses `BenchCameraPath` keyframes with easing, fixed blocks-per-second speeds, holds, per-keyframe captures, and once/loop/ping-pong modes; the scenario calls `BenchCameraPlayback.advance()` once per client tick.
- Screenshot futures are checked across client ticks rather than joined before completion; report artifacts point to non-empty PNG files under the result directory and carry `sha256` plus `bytes`.
- `BenchCaptureOptions.defaults()` defers a capture until `context.environment().readiness().ready()` and consecutive stable frame intervals, and hides the HUD; a gate that never opens captures anyway and invalidates the run rather than hanging.
- Runtime writes a client summary and exits after scenarios complete.
- Window size, VSync, FPS cap, render distance, and simulation distance are configured and recorded; `pauseOnLostFocus` is disabled so an unattended run is never paused by the pause menu.
- The mouse is released every client tick (the cursor stays free and physical mouse movement cannot rotate the player), and `inactivityFpsLimit` is pinned to `MINIMIZED` so the AFK throttle never distorts frame metrics; both are recorded as `client.graphics.*` diagnostics.
- Focus loss, minimize, pause, an opened screen, and window resize are recorded as invalidations and downgrade the run to `INCONCLUSIVE`; focus strictness is controlled by `clientRequireWindowFocus`.
- A failed client scenario automatically captures `failure-<scenario>.png` before the run finalizes.
- `verifyBenchClientExample` demonstrates unattended client E2E with a keyframe camera path, three verified screenshot artifacts, and required environment diagnostics.

## Current limitations

- The current Plugin exposes one default suite and server run rather than the planned multi-suite container.
- Screenshot comparison supports deterministic pixel-difference ratio and mean absolute error, but not perceptual hashing or SSIM.
- JFR, per-scenario JSONL samples, Markdown, and generic failure artifact collection are implemented; GameTest and JUnit XML are not.
- Integrated client automation is implemented, but paired dedicated-server + separate-client orchestration, GUI automatic input, tooltip capture, and a complete visual/accessibility tree are pending.
- The report task performs Draft 2020-12 Schema validation, though the schema still intentionally allows extension fields and can be tightened for phase/metric/artifact details.