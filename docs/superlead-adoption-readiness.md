# SuperLead 接入前准备与测试门禁

> 评估日期：2026-07-26。本文针对 SuperLead 首次接入 ModBench 的 dedicated-server 实验，不代表当前已经完成 SuperLead 业务场景。

## 结论

**可以开始接入。Server 与 Client Provider 都可用于无人值守功能验证；Client Runtime 已能自动进入 integrated world、应用图形基线、固定玩家/相机、执行渲染与帧稳定门禁、采集帧并退出。由于尚无多 fork A/B 统计与硬件噪声控制，仍不应把单次结果当作细粒度性能回归基线。**

当前 ModBench 已经证明通用链路可以工作：外部消费方能够通过 Gradle Plugin 创建 `bench` source set 和 dedicated-server run，Provider 能在 FML 游戏 classloader 中被发现，Runtime 能跨 tick 执行场景并自动停服，生产 JAR 和普通 runtime classpath 能保持隔离。

但在 SuperLead 接入前必须确认版本线和业务 API。当前仓库没有 SuperLead 源码，因此以下项目特定条件需要在 SuperLead 仓库中核对后才能最终判定。

## 硬门禁

以下任一项不满足，就不能直接复用当前 `26.1` API/Runtime：

- Minecraft `26.1.2`；
- NeoForge `26.1.2.76`；
- ModDevGradle `2.0.141` 或兼容的 ModDev 2.x；
- Java 25，且 Gradle daemon 和 Java toolchain 都实际使用 Java 25；
- SuperLead 的目标 Mod 可由 `neoForge.mods` 声明，并有稳定 Mod ID；
- SuperLead 的 Gradle 构建允许 `src/bench` 单向依赖 `src/main`；
- SuperLead 的服务端业务 API 可在 server thread 创建 workload、查询 ready/稳定状态，并精确移除本次测试创建的对象；
- SuperLead 不要求在 Provider 中注册 bench-only Block、EntityType、Dimension 等内容。若需要，必须设计独立 Adapter Mod，而不是让普通 Provider 越权注册。

## 当前 ModBench 能提供什么

Provider 可以访问：

- `MinecraftServer`；
- 默认 `ServerLevel`（当前实现为 overworld）；
- 当前 tick 和 server-thread scheduler；
- cancellation token 与取消原因；
- 固定 seed；
- result directory；
- long/double 指标记录器。

客户端 Provider 还可以访问 `Minecraft`、`ClientLevel`、`LocalPlayer`、client tick scheduler 和 Runtime-owned frame interval 统计。Plugin 会生成 `runBenchClient`；Runtime 自动创建或重用固定 world，执行、写报告并退出。

Provider 可以实现：

- setup；
- 跨 tick stabilize；
- warmup；
- measure；
- verify；
- teardown。

Provider 必须遵守：

- 公共无参构造器；
- `ServiceLoader` 服务描述文件；
- 不创建线程；
- 不阻塞 server thread；
- 不自己管理 Runtime 状态机或报告；
- 所有创建的业务状态都要记录 ID 并可精确清理；
- 失败、超时和部分 setup 后 teardown 仍然安全。

## SuperLead 需要准备的内容

### 1. 构建元数据

记录下列实际值并与 ModBench 对照：

- `minecraft_version`；
- NeoForge 版本；
- ModDevGradle 版本；
- Java toolchain 版本；
- Gradle wrapper 版本；
- SuperLead Mod ID 和版本；
- 是否单 Mod、多 Mod 或包含测试 Adapter Mod；
- 是否有现成的 dedicated-server run；
- 是否启用 configuration cache。

### 2. 业务操作边界

为第一个 Provider 明确这些操作的公开入口和 server-thread 约束：

- 创建固定数量的连接/结构/实体/方块等 workload；
- 判断世界、chunk、SavedData、网络同步或物理状态何时 ready；
- 判断 workload 是否稳定；
- 读取只读诊断指标；
- 按本次场景记录的 UUID/ID 精确删除 workload；
- 清理时不产生掉落、不污染后续场景、不依赖客户端输入。

如果这些 API 目前不存在，优先在 SuperLead 增加 immutable、只读、无副作用的 diagnostics facade；不要把 live simulation、executor、SavedData 写接口或内部集合暴露给 Provider。

### 3. 推荐的第一场景

先做一个 deterministic server smoke，而不是直接做性能排名：

1. 固定 seed 和坐标；
2. setup 创建少量真实 SuperLead workload；
3. stabilize 每 tick 检查服务端权威状态；
4. warmup 执行短时间代表性 workload；
5. measure 记录 server tick 和 SuperLead 自定义计数；
6. verify 检查连接数量、状态、revision/dirty sync 或其他业务不变量；
7. teardown 使用本次记录的 ID 精确清理；
8. 输出 `PASSED` 后自动停服。

第一阶段只回答“工具链和 workload 是否真的跑起来”，不要把当前简化指标当成严肃性能结论。

## 消费方改造形态

SuperLead 项目预计需要：

- 应用 `com.zhongbai233.minecraft-bench`；
- `benchImplementation` 添加匹配的 `bench-api-core` 和 `bench-api-neoforge-26.1`；
- `benchRuntimeMod` 添加 `bench-runtime-neoforge-26.1`；
- `src/bench/java/.../SuperLeadBenchProvider.java`；
- `src/bench/resources/META-INF/services/com.zhongbai233.bench.api.BenchProvider`；
- `modBench.targetMod = "<superlead-mod-id>"`；
- `expectedProviderCount = 1`；
- 固定 seed 和足够覆盖业务 ready 状态的 timeout。

不要把 Provider 放进 `src/main`，不要把 Runtime 放进生产 `implementation` 或普通 `runtimeOnly`。

## 接入验收矩阵

### 构建阶段

- [ ] SuperLead 版本线与当前 API/Runtime 匹配；
- [ ] bench 编译可以引用 Minecraft、NeoForge 和 SuperLead main classes；
- [ ] main 编译看不到 bench classes；
- [ ] Provider 服务描述文件路径和全限定名正确；
- [ ] `benchRuntimeMod` 不出现在普通 `runtimeClasspath`；
- [ ] production JAR 和 sources JAR 不包含 Provider、服务文件和 bench-only resources；
- [ ] configuration cache 连续两次复用。

### 游戏阶段

- [ ] `MODBENCH event=run_start` 出现；
- [ ] Provider 数量为 1 且 classloader 为 FML 游戏 loader；
- [ ] 场景完成 setup/stabilize/warmup/measure/verify/teardown；
- [ ] 业务 workload 使用真实 server-authoritative API；
- [ ] correctness 失败会产生 `FAILED`，而不是误报 `PASSED`；
- [ ] timeout/异常后 partial report 存在；
- [ ] `summary.json` 通过 Schema 验证；
- [ ] `MODBENCH event=run_complete status=PASSED` 出现；
- [ ] dedicated server 自动停服。

### SuperLead 业务阶段

- [ ] workload 创建数量可由报告或诊断验证；
- [ ] readiness 条件不是固定 sleep，而是服务端状态条件；
- [ ] teardown 不会留下连接、实体、方块、SavedData 或 registry 状态；
- [ ] 同一个 game directory 清理后重复运行结果一致；
- [ ] 场景可以在无客户端、无玩家输入条件下运行；
- [ ] 诊断 API 不暴露可变内部控制面。

## 当前阻塞与已知限制

- 报告已填充 `phases`、`metrics`、环境、组件版本与 `loadedMods`，并由通用 Plugin 任务执行正式 Schema 验证；Schema 对 phase/metric/artifact 的细粒度字段约束仍可继续收紧。
- `verifyProductionJarHasNoBenchContent`、`verifyBench*Report`、`collectBench*Artifacts`、timeout thread dump 与 crash/log bundle 均已通用化；仍需在 SuperLead 中实际验证普通 ModDev run 和最终 publication 隔离。
- Server 可固定 seed 与 level type，但普通 Provider 不负责注册 bench-only 游戏内容；需要专用 Dimension、Block 或 EntityType 时仍须设计独立 Adapter Mod。
- Client 已固定玩家/相机并把 focus/minimize/pause/resize 等干扰转换为 `INCONCLUSIVE`；GUI selector 点击、滚动、拖动、按键与 Unicode 输入已实现。paired separate-client passthrough 已能编排 1–8 个隔离客户端和预期断线重连，但 session payload 握手、网络 backend 与真实 SuperLead paired E2E 尚未完成；完整视觉树和感知级截图比对也仍待实现。
- 当前没有真实 SuperLead Provider；SuperLead 业务 E2E 仍是 Phase 1 最后一项未完成验收。

## 建议接入顺序

1. 在 SuperLead 仓库确认版本和 Mod ID，先不改业务 API。
2. 复制独立示例的 Gradle 接入方式，创建只做 server health check 的 Provider。
3. 运行 compile、Provider discovery、production JAR 和普通 runtime classpath 隔离检查。
4. 启动真实 dedicated server，验证 Provider 能发现并自动停服。
5. 加入一个最小真实 SuperLead workload，验证 setup/ready/verify/teardown。
6. 根据第一次失败日志决定是否补业务 diagnostics facade、额外 artifact 或 Runtime API；不要一次性暴露大量内部实现。
7. 通过后再扩大 workload 数量和 measurement 指标。
