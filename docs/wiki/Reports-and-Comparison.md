# 报告与对比 / Reports and comparison

[返回首页 / Home](Home.md) · [图形对照 / Graphics comparison](Graphics-Migration.md)

## 权威报告 / Authoritative result

Runtime 的 `summary.json` 是权威报告，当前 schema 为 `1.0.0`（JSON Schema Draft 2020-12）。旁边的 `report.md` 是派生视图。普通消费方路径：

Runtime's `summary.json` is authoritative, currently schema `1.0.0` using JSON Schema Draft 2020-12. Adjacent `report.md` is a derived view. Ordinary consumer layout:

```text
build/modBench/raw-results/default/server/summary.json
build/modBench/raw-results/default/client/summary.json
build/modBench/raw-results/default/<runType>/report.md
build/modBench/raw-results/default/<runType>/artifacts/
  samples/<scenario>.jsonl
  screenshots/*.png
  gui/*
  custom/*
  thread-dumps/<scenario>.txt
  jfr/<sanitized-scenario-id>.jfr
build/modBench/bundles/default/<runType>/
build/modBench/paired/default/summary.json
```

不同 artifact 按需产生，不是每轮都有全部文件。Runtime 模块自己的 smoke 使用 `build/modBench/raw-results/smoke/server/`。普通 `runBenchServer` / `runBenchClient` 在开始前清理旧结果，并以 finalizer 收集报告、日志和 crash-reports；在下一轮前保存整个结果目录，不要只拷贝 `summary.json`。

Artifacts are conditional. A Runtime module's own smoke uses `build/modBench/raw-results/smoke/server/`. Ordinary server/client runs clean previous results and finalize artifact collection, including logs and crash reports. Preserve the whole result directory before the next run, not just its JSON summary.

报告包含：场景与 phase 起止 tick/时长/结局、指标分布、自定义 artifacts、诊断、OS/CPU/内存/Java/JVM 参数、Minecraft/NeoForge/loaded Mods、Git commit/dirty 状态和运行参数。共享报告前仍应检查日志及自定义 artifact；JVM 参数脱敏不等于所有业务输出都无敏感数据。

Reports include phase timing/outcomes, metric distributions, artifacts, diagnostics, machine/JVM/game/mod metadata, Git state and run parameters. Review logs and custom artifacts before sharing: JVM-argument redaction does not sanitize arbitrary business output.

## 指标含义 / Metric meaning

`server.tick.duration` 是 Runtime 采样的服务器 tick 时长；`client.frame.interval` 是相邻渲染事件的帧间隔，不是 GPU duration。自定义指标通过 `context.metrics().record(...)` 记录，按 phase 汇总 count/min/max/mean/median/P90/P95/P99/stdDev。客户端 MEASURE 帧统计还包含低帧表现与超预算帧计数。

`server.tick.duration` measures server ticks. `client.frame.interval` measures time between render events, not GPU execution. Custom samples are submitted via `context.metrics().record(...)` and aggregated by phase with distribution statistics. Client MEASURE statistics also include low-frame-rate and over-budget-frame information.

性能比较应固定版本、硬件/驱动、电源与温度条件、JVM/heap、模组列表、seed、场景、视距、分辨率、VSync/FPS cap、预热和测量窗口，重复运行并检查原始样本。不要只比较两次平均 FPS，也不要把不同环境的成功状态当作可比较证明。

Control version, hardware/driver, power/thermal state, JVM/heap, mods, seed, scenario, view distance, resolution, VSync/FPS cap, warmup and measurement windows. Repeat runs and inspect raw samples. Two average-FPS numbers or two successful status values do not establish comparability.

## JFR

```kotlin
modBench { jfrEnabled = true }
```

JFR 按已执行的场景录制，文件名来自清理过的场景 ID，例如 `artifacts/jfr/simplebench.server-smoke.jfr`，不是固定 `recording.jfr`。录制会在场景结束/失败路径中尝试保存；JFR 不可用或写入失败应查看 diagnostics。用 JDK Mission Control 或 `jfr summary <file>` 检查录制，不要把缺失录制视为成功。

JFR records executed scenarios individually, for example `artifacts/jfr/simplebench.server-smoke.jfr`, rather than a fixed `recording.jfr`. Runtime attempts to preserve recordings on completion/failure. Inspect diagnostics if recording is unavailable or writing fails. Open the result with JDK Mission Control or `jfr summary <file>`.

## 验收任务 / Verification tasks

| Task | Starts Minecraft? | Purpose |
| --- | --- | --- |
| `check` | No benchmark run | Tests and production/source JAR isolation |
| `runBenchServer` / `runBenchClient` | Yes | Execute scenarios |
| `verifyBenchServerReport` / `verifyBenchClientReport` | No | Validate existing JSON plus configured expectations |
| `verifyBenchServer` / `verifyBenchClient` | Yes | Run and validate |

默认报告验收要求 `PASSED`，再检查 schema、runType 与配置的场景、指标、artifact、diagnostic、loaded Mods。`expectedScenarioIds` 检查包含期望 ID，不表示场景集合必须完全相同；metric-name 期望也不是对每个场景逐一断言。复杂业务验收应明确检查对应场景的证据。

Report verification defaults to `PASSED`, then validates schema, run type and configured evidence. Expected scenario IDs require presence rather than an exact set, and metric-name expectations are not automatically per-scenario assertions. Add explicit checks for complex business acceptance.

## 对比能力的边界 / Comparison boundaries

- 通用性能 A/B 自动回归、`compareBench` Gradle 任务和 JUnit XML 报告目前不应视为已提供
- `BenchImageDiff.compare(a, b)` 提供像素差异比例和 MAE，不是感知级视觉判断
- 26.2/26.3 的 Python 工具专门比较 graphics-migration 证据，不接受任意 summary 或截图替代

A generic performance-regression engine, `compareBench` task and JUnit XML output are not established capabilities. `BenchImageDiff` provides pixel difference ratios/MAE, not perceptual judgments. The 26.2/26.3 Python comparator is specifically for graphics-migration evidence.

Runtime 使用 `PASSED` / `FAILED` / `INCONCLUSIVE` 等运行状态。图形 sidecar 使用 `PASS` / `FAIL` / `BLOCKED`，可选 probe 可为 `SKIP`；对比结果为 `PASS` / `FAIL` / `INCONCLUSIVE`。保留原始状态词，不要把三层状态混合，也不要把缺证据变成 PASS。

Preserve the distinct status vocabularies: Runtime run outcomes, graphics-probe outcomes, and comparison outcomes. Missing, invalid, blocked or skipped evidence must never become a pass.
