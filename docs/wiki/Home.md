# BenchMod Wiki / 使用指南

[中文 README](https://github.com/zhongbai2333/BenchMod/blob/main/README.md) · [English README](https://github.com/zhongbai2333/BenchMod/blob/main/README.en.md)

BenchMod 为 NeoForge Mod 提供可复现的游戏内基准测试：Gradle 负责准备和验收，Runtime 在真实游戏中执行，Provider 描述你的业务负载。代码中的 `ModBench`、`modBench`、`modbench_runtime` 是现有 API/插件名称，不要跟随仓库名称改写。

BenchMod runs reproducible in-game benchmarks for NeoForge mods. Gradle prepares and verifies each run, the Runtime executes inside Minecraft, and a test-only Provider supplies your workload. Existing identifiers such as `ModBench`, `modBench`, and `modbench_runtime` remain unchanged.

## 从这里开始 / Start here

1. [版本与架构 / Versions and architecture](Versions-and-Architecture.md)：先选对应的 Minecraft 分支，再选 API 与 Runtime
2. [快速接入 / Getting started](Getting-Started.md)：构建、独立消费方、JitPack 与本地开发
3. [编写场景 / Writing scenarios](Writing-Scenarios.md)：Provider、生命周期、指标与隔离
4. [客户端自动化 / Client automation](Client-Automation.md)：世界、相机、截图、GUI 与 paired 模式
5. [报告与对比 / Reports and comparison](Reports-and-Comparison.md)：JSON、原始样本、JFR、验收与结果解释
6. [图形移植 / Graphics migration](Graphics-Migration.md)：26.2/26.3 的真实 GPU 探针与 OpenGL/Vulkan 对照
7. [贡献与排错 / Contributing and troubleshooting](Contributing-and-Troubleshooting.md)：检查清单、常见问题与发布边界

首次接入建议先跑独立示例的 dedicated-server 验收，再写真实 workload，最后接入需要显示器/GPU 的客户端场景。

For a first integration, verify the standalone dedicated-server example, add your real workload, then run client scenarios on a machine with a display and graphics device.

## 证据与边界 / Evidence and limits

- `check`、编译和无设备 CI 通过，不能证明真实客户端、GPU 像素或性能已验证
- 客户端 frame interval 是帧间隔，不是 GPU 执行时间
- `INCONCLUSIVE`、`BLOCKED`、`SKIP` 不能按成功处理
- 分支存在、源码版本号或 Maven Local 可用，不等于已经发布了可用的 JitPack tag
- 1.21.1 为服务端已验收的 beta；客户端图形 E2E 仍未验证

Passing builds and headless CI do not establish client/GPU correctness or benchmark performance. Frame intervals are not GPU timings. Inconclusive, blocked, or skipped evidence is not a pass. A source version or branch does not prove that a release artifact is available. The 1.21.1 backport is a server-validated beta; graphical client E2E remains unverified.

本 Wiki 的可评审源文件位于仓库 `docs/wiki/`。代码、版本对应的构建配置与可复现结果优先于历史设计文档；[ADR](https://github.com/zhongbai2333/BenchMod/tree/main/docs/adr) 解释边界，[实施计划](https://github.com/zhongbai2333/BenchMod/blob/main/docs/mod_bench_implementation_plan.md) 描述长期方向。

Reviewable sources for this Wiki live in `docs/wiki/`. Prefer version-specific code, build configuration, and reproducible results over historical plans. ADRs explain the boundaries; the implementation plan describes future direction.
