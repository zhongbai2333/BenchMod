# ModBench 文档导航

这里集中说明每份文档的用途。阅读和维护时，以“当前事实”文档为准；设计计划用于解释方向，不能替代已经验证的实现状态。

## 从哪里开始

| 需求 | 文档 | 说明 |
| --- | --- | --- |
| 了解当前完成度与已知限制 | [implementation_status.md](implementation_status.md) | 当前实现的事实来源，功能变化后同步更新。 |
| 在外部 NeoForge Mod 中接入 | [consumer-quickstart.md](consumer-quickstart.md) | JitPack、Gradle Plugin、Provider 与运行命令。 |
| 验证最小消费方 | [示例项目](../examples/simple-neoforge-mod/README.md) | 默认验证最新已确认的 JitPack 版本，也支持显式切换 Maven Local。 |
| 准备和验证新版本 | [releasing.md](releasing.md) | 发布前检查、tag、JitPack 和发布后验收。 |
| 理解架构约束 | [adr/README.md](adr/README.md) | 已接受的架构决策及其背景。 |
| 查看长期设计与阶段路线 | [mod_bench_implementation_plan.md](mod_bench_implementation_plan.md) | 设计基线和路线，不表示其中所有内容已经实现。 |
| 评估 SuperLead 试点接入 | [superlead-adoption-readiness.md](superlead-adoption-readiness.md) | 特定消费方的门禁、边界和建议顺序。 |

## 信息优先级

文档发生冲突时按以下顺序判断：

1. 可重复的构建、测试和外部消费验证结果；
2. `implementation_status.md` 记录的当前状态；
3. 已接受的 ADR；
4. 实施计划与特定项目接入建议。

版本也分为两条用途不同的线：

- 根目录 `gradle.properties` 的 `modBenchVersion` 是当前源码/下一次 tag 的版本；
- `examples/simple-neoforge-mod/gradle.properties` 的 `modbench_version` 是最后一个已在 JitPack 验证可用的版本。

准备新版本时先调整并验证根版本；JitPack 构建成功后，再更新示例消费版本。不要为了保持两个文件表面一致而提前让示例依赖尚不存在的 tag。
