# BenchMod 文档 / Documentation

[简体中文 README](../README.md) · [English README](../README.en.md) · [GitHub Wiki](https://github.com/zhongbai2333/BenchMod/wiki)

## 使用指南 / User guides

中英双语指南的版本化源文件在 [wiki/Home.md](wiki/Home.md)，并同步至原生 GitHub Wiki。
The versioned bilingual guides below are mirrored to the native GitHub Wiki.

| Guide | 内容 / Contents |
| --- | --- |
| [Versions and architecture](wiki/Versions-and-Architecture.md) | 分支、工具链、API / Runtime / Provider 边界 |
| [Getting started](wiki/Getting-Started.md) | 构建、独立示例、JitPack、本地消费 |
| [Writing scenarios](wiki/Writing-Scenarios.md) | Provider、生命周期、指标、验收 |
| [Client automation](wiki/Client-Automation.md) | 世界、相机、截图、GUI、paired 模式 |
| [Reports and comparison](wiki/Reports-and-Comparison.md) | JSON、JSONL、JFR、artifact、比较边界 |
| [Graphics migration](wiki/Graphics-Migration.md) | 26.2 / 26.3 可选 GPU 探针和双后端流程 |
| [Contributing and troubleshooting](wiki/Contributing-and-Troubleshooting.md) | 贡献、排错、验证与发布 |

## 深入参考 / Reference

以下原有文档保留设计背景与详细记录。开发线和当前能力以对应分支源码、配置及可复现验收为准；历史阶段描述不能替代新版本证据。
These documents preserve design context and detailed records. Prefer the matching branch's code, configuration and reproducible validation over historical stage descriptions.

- [26.1 consumer setup](consumer-quickstart.md) and [standalone example](../examples/simple-neoforge-mod/README.md)
- [Implementation status](implementation_status.md)
- [Architecture decision records](adr/README.md)
- [Long-term implementation plan](mod_bench_implementation_plan.md)
- [Release process](releasing.md)
- [SuperLead adoption readiness](superlead-adoption-readiness.md)

根 `modBenchVersion` 与示例的 `modbench_version` 不要求相等：前者标识源码/本地发布，后者固定消费方依赖。不要提前把示例指向尚未验证可用的远程 tag。
The root source/local-publication version and a consumer's pinned dependency version need not match. Do not point consumers at an unverified remote tag.

## 当前分支参考 / This branch

- [Minecraft 26.3 port](port-26.3.md)
- [26.3 standalone consumer](../examples/simple-neoforge-mod-26.3/README.md)
- [Graphics migration detail](graphics-migration-assistant-26.3.md)
