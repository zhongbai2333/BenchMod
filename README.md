# BenchMod

**简体中文** · [English](README.en.md) · [Wiki](https://github.com/zhongbai2333/BenchMod/wiki) · [文档索引](docs/README.md)

面向 NeoForge Mod 开发者的可复现游戏内基准测试工具链。把真实业务负载写在独立的 `src/bench` 中，由 BenchMod 启动游戏、执行场景、采集指标、保存证据并验收结果。

> 当前分支：`26.3` · Minecraft **26.3** · NeoForge **26.3.0.45-beta** · Java **25**
> 本分支同时保留 26.1 基线；26.3 请使用下方专用示例，插件不会自动提供 26.2 适配器。

## 能做什么

- **服务端测试**：跨 tick 生命周期、确定性 seed、业务断言、自定义指标、超时和自动退出
- **客户端自动化**：世界供给、相机路径/构图、渲染就绪门禁、PNG 截图、GUI 交互与帧间隔采样
- **可追溯证据**：权威 JSON、原始 JSONL 样本、Markdown 报告、逐场景 JFR、日志与失败产物
- **独立消费与隔离**：Gradle 插件注入匹配 API/Runtime，检查生产 JAR 不包含 bench 内容
- **高版本图形移植**：26.2/26.3 可选六项真实 GPU 正确性探针及严格 OpenGL/Vulkan 对照

编译或 headless CI 通过不代表 GPU 实测通过。帧间隔不是 GPU 时间；无效环境、缺设备和缺失证据不能算成功。高版本图形 suite 尚无已验证的双后端硬件运行结论，见 [验证边界](docs/wiki/Graphics-Migration.md)。

## 版本选择

| Branch | Minecraft | NeoForge | Java | 状态 / Status |
| --- | --- | --- | --- | --- |
| [main](https://github.com/zhongbai2333/BenchMod/tree/main) | 26.1.2 | 26.1.2.76 | 25 | 基线 / baseline |
| [26.2](https://github.com/zhongbai2333/BenchMod/tree/26.2) | 26.2 | 26.2.0.88 | 25 | 版本适配 + 图形探针 / port + graphics suite |
| [26.3](https://github.com/zhongbai2333/BenchMod/tree/26.3) | 26.3 | 26.3.0.45-beta | 25 | 版本适配 + 图形探针 / port + graphics suite |
| 1.21.1 | 1.21.1 | 21.1.252 | 21 | 移植中，尚未验收 / port in progress, not validated |

分支版本不能混用。`bench-api-neoforge-<line>` 后缀表示适配线；`modBenchVersion` 表示工具自身源码版本，不等于已经发布的 tag。完整模块映射见 [版本与架构](docs/wiki/Versions-and-Architecture.md)。

## 五分钟起步

这是开发工具链，不是把单个 JAR 放入玩家 `mods` 文件夹就完成接入。需要对应 JDK、网络访问和仓库自带的 Gradle Wrapper；26.x CI 同时提供 Java 21 与 25。

从**当前分支的仓库根目录**执行：

```sh
./gradlew -PmodBenchBinaryArtifacts=true check verifyReleaseReadiness
./gradlew -p examples/simple-neoforge-mod-26.3 -PmodBenchBinaryArtifacts=true compileBenchJava check
./gradlew -p examples/simple-neoforge-mod-26.3 -PmodBenchBinaryArtifacts=true verifyBenchServer
```

`verifyReleaseReadiness` 会检查并发布当前源码到 Maven Local。独立示例会启动真实专服、验收报告并退出；Windows PowerShell 把 `./gradlew` 换成 `.\gradlew.bat`。客户端需有可用显示器与图形驱动，再将最后一个任务替换为 `verifyBenchClient`。

报告位于 `examples/simple-neoforge-mod-26.3/build/modBench/raw-results/default/server/summary.json`。每次运行会清理旧结果，要做对照请先保存整个结果目录。

接入自己的 Mod：应用 `com.zhongbai233.minecraft-bench`，在 `src/bench` 编写 Provider 和 ServiceLoader descriptor，再设置报告期望。完整步骤、已发布 JitPack 消费和本地开发的区别见 [快速接入](docs/wiki/Getting-Started.md)。

## 工作方式

| 层 | 职责 |
| --- | --- |
| Gradle Plugin | 隔离 bench source set，准备 ModDev run，验收报告与收集产物 |
| Core / NeoForge API | Provider SPI、版本契约、场景/指标与客户端自动化接口 |
| Runtime Mod | 在游戏内调度生命周期、采样、执行自动化并写报告 |
| 你的 Provider | 创建真实业务负载、记录指标、验证结果和精确清理 |

`src/bench` 可以依赖 `src/main`，生产代码不能反向依赖 bench。代码沿用 `ModBench`、`modBench`、`modbench_runtime` 等现有标识，仓库名是 BenchMod。

## 继续阅读

- [编写场景](docs/wiki/Writing-Scenarios.md)：完整 Provider 示例、生命周期与指标
- [客户端自动化](docs/wiki/Client-Automation.md)：相机、截图、GUI 和 paired 模式
- [报告与对比](docs/wiki/Reports-and-Comparison.md)：产物、JFR、验收和比较边界
- [图形移植助手](docs/wiki/Graphics-Migration.md)：26.2/26.3 六项 GPU 探针与双后端流程
- [贡献与排错](docs/wiki/Contributing-and-Troubleshooting.md)：测试、发布和常见问题
- [架构决策](docs/adr/README.md) · [历史实施状态](docs/implementation_status.md) · [长期设计](docs/mod_bench_implementation_plan.md)

## 贡献与许可证

欢迎带最小复现、目标版本和完整报告提交 issue/PR。保持生产包隔离，为行为修改补测试；客户端改动请区分编译验证与真实图形验证。详见 [贡献指南](docs/wiki/Contributing-and-Troubleshooting.md)。

[MIT License](LICENSE)
