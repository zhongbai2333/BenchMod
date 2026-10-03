# BenchMod

[简体中文](README.md) · **English** · [Wiki](https://github.com/zhongbai2333/BenchMod/wiki) · [Documentation](docs/README.md)

A reproducible in-game benchmarking toolkit for NeoForge mod developers. Keep real workloads in an isolated `src/bench` source set; BenchMod launches the game, runs scenarios, samples metrics, preserves evidence and verifies the results.

> Current branch: `26.3` · Minecraft **26.3** · NeoForge **26.3.0.45-beta** · Java **25**
>
> This branch also retains the 26.1 baseline. Use the dedicated 26.3 example below; this plugin does not supply a 26.2 adapter.

## What it does

- **Server benchmarks:** tick-driven lifecycle, deterministic seeds, business assertions, custom metrics, timeouts and automatic shutdown
- **Client automation:** world provisioning, camera paths/framing, readiness gates, PNG captures, GUI interactions and frame-interval sampling
- **Traceable evidence:** authoritative JSON, raw JSONL samples, Markdown reports, per-scenario JFR, logs and failure artifacts
- **Independent integration:** matching API/Runtime dependencies and production-JAR isolation checks
- **Graphics migration:** opt-in six-probe real-GPU correctness suite and strict OpenGL/Vulkan comparison on 26.2/26.3

Passing compilation or headless CI is not a GPU pass. Frame intervals are not GPU timings; invalid environments, missing devices and missing evidence cannot count as success. The high-version suite has no verified dual-backend hardware result yet; see [validation limits](docs/wiki/Graphics-Migration.md).

## Choose a version

| Branch | Minecraft | NeoForge | Java | Status |
| --- | --- | --- | --- | --- |
| [main](https://github.com/zhongbai2333/BenchMod/tree/main) | 26.1.2 | 26.1.2.76 | 25 | Baseline |
| [26.2](https://github.com/zhongbai2333/BenchMod/tree/26.2) | 26.2 | 26.2.0.88 | 25 | Port + graphics suite |
| [26.3](https://github.com/zhongbai2333/BenchMod/tree/26.3) | 26.3 | 26.3.0.45-beta | 25 | Port + graphics suite |
| [1.21.1](https://github.com/zhongbai2333/BenchMod/tree/1.21.1) | 1.21.1 | 21.1.252 | 21 | Server-validated beta; graphical client unverified |

Do not mix version branches. A `bench-api-neoforge-<line>` suffix identifies an adapter line; `modBenchVersion` is the toolkit source version and does not prove that a release tag exists. See [versions and architecture](docs/wiki/Versions-and-Architecture.md).

## Quick start

This is a development toolchain, not a single JAR installation for a player's `mods` folder. Use the matching JDKs, network access and the checked-in Gradle Wrapper. The 26.x CI environment provides both Java 21 and 25.

From the repository root on **this branch**:

```sh
./gradlew -PmodBenchBinaryArtifacts=true check verifyReleaseReadiness
./gradlew -p examples/simple-neoforge-mod-26.3 -PmodBenchBinaryArtifacts=true compileBenchJava check
./gradlew -p examples/simple-neoforge-mod-26.3 -PmodBenchBinaryArtifacts=true verifyBenchServer
```

`verifyReleaseReadiness` checks and publishes the current source to Maven Local. The independent example launches a real dedicated server, verifies its report and exits. In Windows PowerShell, replace `./gradlew` with `.\gradlew.bat`. On a machine with a display and working graphics drivers, replace the final task with `verifyBenchClient` to exercise the client.

The report is written to `examples/simple-neoforge-mod-26.3/build/modBench/raw-results/default/server/summary.json`. Runs clean previous results, so preserve the whole result directory before running a comparison.

To integrate your own mod, apply `com.zhongbai233.minecraft-bench`, add a Provider and ServiceLoader descriptor under `src/bench`, and configure report expectations. See [Getting started](docs/wiki/Getting-Started.md) for complete integration and the distinction between released JitPack consumption and local development.

## How it fits together

| Layer | Responsibility |
| --- | --- |
| Gradle Plugin | Isolate bench sources, prepare ModDev runs, verify reports and collect artifacts |
| Core / NeoForge API | Provider SPI, compatibility contracts, scenarios, metrics and client automation interfaces |
| Runtime Mod | Own in-game scheduling, sampling, automation and report writing |
| Your Provider | Build real workloads, record metrics, assert correctness and clean up owned state |

`src/bench` may depend on `src/main`; production code must not depend on bench code. Existing code identifiers remain `ModBench`, `modBench` and `modbench_runtime`; the repository is named BenchMod.

## Guides

- [Writing scenarios](docs/wiki/Writing-Scenarios.md): complete Provider example, lifecycle and metrics
- [Client automation](docs/wiki/Client-Automation.md): cameras, screenshots, GUI and paired mode
- [Reports and comparison](docs/wiki/Reports-and-Comparison.md): artifacts, JFR, verification and evidence limits
- [Graphics migration](docs/wiki/Graphics-Migration.md): six probes and the 26.2/26.3 dual-backend workflow
- [Contributing and troubleshooting](docs/wiki/Contributing-and-Troubleshooting.md): checks, releases and common failures
- [Architecture decisions](docs/adr/README.md) · [Historical implementation status](docs/implementation_status.md) · [Long-term design](docs/mod_bench_implementation_plan.md)

## Contributing and license

Include a minimal reproduction, target version and complete report when opening an issue or PR. Preserve production isolation, add regression tests, and distinguish compilation from real graphical validation. See the [contribution guide](docs/wiki/Contributing-and-Troubleshooting.md).

[MIT License](LICENSE)
