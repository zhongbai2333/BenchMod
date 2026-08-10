# ModBench 通用 NeoForge 基准测试工具链实现设计

> **文档状态：Phase 1 Server MVP 已有可运行纵切，后续条目继续作为实现路线与验收基线。**
>
> 建议正式落盘路径：`docs/mod_bench_implementation_plan.md`。本文描述一个可供多个 NeoForge Mod 复用的自动化游戏内基准测试体系，而不是 Super Lead 专属工具。
>
> 推荐阅读顺序：先看第 2、3、6、7 节理解三层架构和运行时模型，再看第 15 节实施路线与第 17 节验收标准；准备编码时再逐项参考 Gradle、SPI、报告和测试矩阵章节。

## 1. 目标与非目标

### 1.1 目标

构建一套跨仓库复用的工具链，使消费方 Mod 只需应用 Gradle 插件并在 `src/bench` 提供 Provider，即可获得：

- 隔离的 Bench source set 与游戏目录；
- 自动生成的 server/client/GameTest run；
- 游戏内 setup → stabilize → warmup → measure → verify → teardown 生命周期；
- 专用虚空世界/固定场景/连续镜头（客户端阶段）；
- 标准 JVM、tick、frame、GC、JFR 指标；
- 被测 Mod 自定义指标与正确性断言；
- JSON/JSONL/Markdown/JUnit XML 报告；
- CI 产物收集、schema 验证和后续 baseline 比较；
- 保证 Bench Provider 不进入业务 Mod 的生产 JAR。

### 1.2 非目标

第一版不追求：

- 一个 Runtime JAR 跨越所有 Minecraft 大版本；
- 自定义 NeoForge registry 用于 Provider 发现；
- 自动扫描/反射业务 Mod 私有实现；
- 在 Gradle daemon 中运行 Minecraft 或采集游戏指标；
- 共享 CI 上检测 2%–5% 的微小性能差异；
- 第一版就实现 Web dashboard、分布式执行或跨硬件排名。

## 2. 总体架构

采用三层模型：

1. **Gradle Plugin（构建期编排）**：创建 source set、配置依赖与 ModDev runs、转发参数、收集并验证产物。
2. **Bench Runtime Mod（游戏内执行引擎）**：发现 Provider、管理世界与场景生命周期、采样、报告、超时与退出。
3. **Target Bench Provider（业务测试插件）**：位于各业务 Mod 的 test-only `src/bench`，描述场景、workload、专属指标和正确性断言。

Gradle 插件回答“怎么启动和怎么验收”；Runtime Mod 回答“游戏里怎样执行和采样”；Provider 回答“这个业务 Mod 测什么”。

## 3. 建议仓库与发布模块

建立独立 `ModBench` 仓库，初始包含：

- `bench-api-core`：纯 Java descriptor、status、metric、report、compatibility、parameter schema。
- `bench-api-neoforge-26.1`：对应 Minecraft/NeoForge 开发线的 server/client context 与调度 API。
- `bench-runtime-neoforge-26.1`：真正的 NeoForge Runtime Mod。
- `bench-gradle-plugin`：可发布 Gradle binary plugin。
- `bench-report-schema`：JSON Schema、示例、迁移规则。
- `bench-testkit`：Gradle TestKit fixtures。
- `examples/simple-neoforge-mod`：最小端到端消费示例。

可选后续模块：

- `bench-scenarios-common`：标准空世界、固定 tick、基础相机路线等场景构件。
- `bench-smoke-target`：CI 专用最小 NeoForge Mod。

建议坐标（最终域名可调整）：

- Gradle plugin ID：`com.zhongbai233.minecraft-bench`
- Core API：`com.zhongbai233.bench:bench-api-core:<version>`
- NeoForge API：`com.zhongbai233.bench:bench-api-neoforge-26.1:<version>`
- Runtime：`com.zhongbai233.bench:bench-runtime-neoforge-26.1:<version>`
- Report schema：`com.zhongbai233.bench:bench-report-schema:<version>`

Gradle plugin、Core API、Runtime 和 report schema 使用独立 SemVer，不强制同号。

## 4. 消费方项目布局

每个被测 Mod 使用：

- `src/main`：生产 Mod。
- `src/test`：普通 JUnit。
- `src/bench`：游戏进程内 Provider、场景与 bench-only resources。
- 可选 `src/benchTest`：Provider/统计器/状态机的纯 JUnit。

`bench` 必须单向依赖 `main`；`main` 绝不能依赖 `bench`。

Provider 服务描述放在：

- `src/bench/resources/META-INF/services/<BenchProvider 全限定名>`

Bench-only worldgen、structure、GameTest 数据使用业务 Mod 自己的 bench namespace，不能写入生产 `src/main/resources`。

## 5. Gradle 插件设计

### 5.1 职责

插件应：

1. 等待 `net.neoforged.moddev` 应用后，通过公开 DSL 配置。
2. 创建一个或多个 Bench suite；每个 suite 对应一个 source set 和一个 target Mod。
3. 创建 `benchImplementation`、`benchCompileOnly`、`benchRuntimeOnly`、`benchRuntimeMod` 等配置。
4. 调用 ModDev `addModdingDependenciesTo(sourceSet)`。
5. 将 target main output 添加到 bench compile/runtime classpath。
6. 将 Core/NeoForge API 加入编译路径，将 Runtime Mod 只加入 bench runtime path。
7. 注册 ModDev runs，不自行拼接 JavaExec/classpath。
8. 使用独立 gameDirectory 与 resultDirectory。
9. 只转发白名单 `-PmodBench.*` 属性，禁止透传凭据/签名信息。
10. 注册 report schema 验证、artifact collection、生产 JAR 污染检查。
11. 保持 configuration cache 兼容。

### 5.2 禁止依赖的实现细节

插件不得依赖：

- ModDev internal task 类；
- `PrepareRun` / `WriteLegacyClasspath`；
- 生成的 `build/moddev/*Args.txt`；
- `devLaunchConfig`、`MOD_CLASSES`；
- 反射内部 model；
- 自己实现 Minecraft JavaExec 启动器。

仅使用公开边界：`ModDevExtension`、`RunModel`、`ModModel`、`addModdingDependenciesTo`、`runs`、`loadedMods`、`sourceSet`、`gameDirectory`、system/program/JVM arguments。

### 5.3 Suite DSL

插件内部从 `NamedDomainObjectContainer<BenchSuiteSpec>` 建模，支持：

- `targetMod`
- `loadedMods`
- `sourceSetName`
- `client.enabled`
- `server.enabled`
- `gameTestServer.enabled`
- `gameTestNamespaces`
- `gameDirectory`
- `resultDirectory`
- `systemProperties`
- `programArguments`
- `jvmArguments`
- `forwardedProperties`
- `artifactIncludes`
- `runtimeVersion`

单 Mod 项目可默认选唯一 Mod；多 Mod 项目必须显式指定 `targetMod`，不得取容器第一个。

### 5.4 任务与运行配置

默认 suite 生成：

- `compileBenchJava`
- `processBenchResources`
- `runBenchServer`
- `runBenchClient`
- `runBenchGameTestServer`
- `collectBenchArtifacts`
- `verifyBenchReport`
- `verifyProductionJarHasNoBenchContent`

其他 suite 使用稳定、可检测碰撞的任务命名规则。

默认 game directory 建议位于：

- `build/modBench/runs/<suite>/server`
- `build/modBench/runs/<suite>/client`
- `build/modBench/runs/<suite>/gameTestServer`

原始报告建议位于：

- `build/modBench/raw-results/<suite>/<runType>`

收集后的 bundle 位于：

- `build/modBench/bundles/<suite>/<runType>`

### 5.5 发布隔离

必须验证：

- 生产 `jar`、`sourcesJar`、`components.java`、Maven publication 只包含 `main`。
- 不含 Provider 包、`META-INF/services/BenchProvider`、bench metadata、bench worldgen 和 bench classes。
- `benchRuntimeMod` 不进入普通 `runtimeClasspath` 或普通 `runClient`。
- `benchRuntimeMod` 只进入 Bench runtime classpath。

## 6. Provider SPI

### 6.1 发现机制

默认使用 `ServiceLoader<BenchProvider>`。

原因：

- Provider 可只是 test-only classpath 内容；
- 不要求独立 `@Mod`；
- 不污染业务 Mod metadata；
- 普通 JVM 测试可直接实例化；
- 跨仓库、跨 Gradle 项目简单。

Runtime 必须：

- 显式使用正确游戏 classloader；
- 捕获并报告 `ServiceConfigurationError`；
- 按 Provider ID 排序；
- 检测重复 ID；
- 记录 Provider 类名、来源 artifact、目标 Mod、API 版本；
- 不跨世界永久缓存 Provider 实例；
- 将“发现 0 Provider”与预期 Provider 数不符视为失败。

Provider 构造器必须公开、无参、轻量，禁止访问世界/registry/server 或创建线程。

### 6.2 核心接口

Core API 建议包含：

- `BenchProvider`
- `BenchRegistrar`
- `ScenarioDescriptor`
- `BenchScenarioFactory`
- `BenchStatus`
- `BenchMetricDescriptor`
- `BenchParameterSchema`
- `BenchCompatibility`
- `BenchArtifactDescriptor`

NeoForge API 建议包含：

- `BenchServerContext`
- `BenchClientContext`
- `BenchIntegratedContext`
- `BenchScheduler`
- `BenchWorldController`
- `BenchMetricRecorder`
- `BenchArtifactWriter`
- `BenchCancellationToken`

不要让 Provider 自己开线程、写另一套报告或掌控整个执行状态机。

### 6.3 高级 Adapter Mod 模式

如果某场景需要注册 bench-only Block、EntityType、Menu、Payload 或 Dimension 内容，ServiceLoader Provider 不够。此时允许独立测试 Adapter Mod：

- 有独立 `@Mod` 和 metadata；
- required 依赖目标 Mod 与 Runtime；
- 只在 Bench run 加载；
- 通过 Runtime API或可选 IMC 注册场景。

这属于高级模式，不应成为普通 Provider 的必需样板。

不使用自定义 NeoForge Registry 作为 Provider 主发现机制。

## 7. Runtime Mod 执行模型

统一状态机：

1. `DISCOVER`
2. `VALIDATE`
3. `WAIT_RUNTIME_READY`
4. `SETUP`
5. `STABILIZE`
6. `WARMUP`
7. `MEASURE`
8. `VERIFY`
9. `TEARDOWN`
10. `FINALIZE`
11. `SHUTDOWN_OR_KEEP_OPEN`

每阶段拥有：

- 主线程/服务端/客户端调度约束；
- timeout；
- cancellation；
- start/end marker；
- partial result flush；
- finally teardown。

场景状态：

- `PASSED`
- `FAILED`
- `SKIPPED`
- `INCOMPATIBLE`
- `TIMED_OUT`
- `ABORTED`
- `INCONCLUSIVE`

Provider 初始化失败不能让其他 Provider 静默消失，但 CI 默认将加载失败计为 run failure。

### 7.1 服务端权威路径

业务场景必须在 server thread 使用目标 Mod 正式 API创建真实状态，不能由客户端伪造镜像。

以 Super Lead 为例：

- 服务端放置支撑方块；
- 等待 chunk/block 更新稳定；
- 调用 `SuperLeadNetwork.connect(...)`；
- 等待 SavedData、dirty sync、客户端 revision 和 static/dynamic 状态达到 ready；
- 再开始 warmup/measurement；
- teardown 使用 `removeConnectionsWithoutDrops(...)` 精确删除 Provider 记录的 UUID。

Runtime 只提供编排和等待机制，不硬编码目标 Mod 的业务操作。

## 8. Server MVP

第一阶段只实现 dedicated server 闭环：

- 固定 seed 和临时 game directory；
- ServiceLoader 发现；
- setup/stabilize/warmup/measure/verify/teardown；
- tick duration 与 JVM/GC 基础指标；
- JSON 报告；
- partial report；
- 自动停服；
- timeout 时 thread dump、日志、崩溃产物；
- Gradle 任务验证报告存在、schema 正确、场景完成。

Super Lead 作为试点 Provider，只实现一个确定性真实服务端场景，例如固定数量 connection 的创建、稳定、固定 tick workload、正确性断言与清理。

MVP 暂不做客户端 FPS、镜头、JFR 自动分析、统计显著性和 dashboard。

## 9. Client Bench 阶段

第二阶段增加 integrated client：

- 专用虚空世界或 benchmark dimension；
- 自动创建/快速进入；
- 固定窗口、分辨率、VSync、FPS cap、视距；
- 固定玩家和连续 camera spline；
- world/resource/chunk/mesh 稳定等待；
- frame interval 采样；
- 生产 Mod 自定义只读 diagnostics；
- JFR 自动启停；
- JSONL 原始样本、summary JSON、Markdown；
- 自动退出或 `keepOpen`。

必须记录并验证：窗口失焦/最小化、暂停、resize、shader/resource pack、模组列表、JVM 参数和硬件。无 GPU query 时报告 `gpu_frame_time=unavailable`，不可把 frame interval 冒充 GPU 时间。

## 10. 指标系统

标准指标：

- server tick duration；
- client frame interval；
- heap/non-heap/direct buffer；
- GC count/time；
- thread count；
- process/system CPU；
- 可用时 thread allocated bytes；
- JFR CPU/allocation/GC/locks/park/IO；
- harness overhead。

每个 Metric 描述：

- `name`
- `unit`
- `direction`（lower/higher/neutral）
- `aggregation`
- `tags`
- `samples`
- `summary`

高频路径使用预分配 primitive buffer，不在每 tick/frame 构造 JSON、Map 或字符串；结束后再序列化。

Server/tick 和 Client/frame 统计至少输出 mean、median、P90、P95、P99、max、标准差/MAD、over-budget 数量。客户端额外输出 1% low、0.1% low 和 >16.67/33.33/50/100ms 帧数。

## 11. Report Schema

JSON 为权威格式，JUnit XML 仅用于 CI 展示。

顶层：

- `schema`
- `run`
- `environment`
- `artifacts`
- `providers`
- `scenarios`
- `summary`
- `diagnostics`

必须记录：

- OS/CPU/内存；
- Java vendor/version/VM/JVM args；
- Minecraft/NeoForge/FML；
- plugin/API/runtime/schema 版本；
- loaded mods 与版本；
- target git commit/dirty；
- CI identity；
- seed、side、suite、run type；
- warmup/measurement 参数；
- workload correctness；
- failure/skip/timeout 原因。

Schema 独立 SemVer：新增可选字段升 minor；删除/改名/单位变化升 major；解析器忽略未知字段。

Client 完整产物建议：

- `manifest.json`
- `samples.jsonl`
- `summary.json`
- `report.md`
- `benchmark.log`
- `recording.jfr`
- `failure.txt`

日志使用固定可 grep marker：`MODBENCH event=run_start|phase_start|phase_end|run_complete|run_failed`。

## 12. 版本兼容

分别管理：

- Gradle plugin SemVer；
- Core API SemVer；
- NeoForge API 按 MC 开发线；
- Runtime 按 MC/NeoForge 开发线；
- Report schema SemVer；
- Provider 声明 API major/minor 兼容范围。

首版明确支持：

- ModDevGradle 2.x；
- 当前验证重点：Gradle 9.5、ModDev 2.0.141、MC 26.1、Java 25；
- 通过 TestKit 扩展至 Gradle 8.8+ 与 legacy/modern classpath 两条分支。

不通过反射兼容 ModDev 1.x；需要时发布单独 adapter/plugin major。

## 13. Gradle TestKit 与测试矩阵

必须覆盖：

- Groovy/Kotlin DSL；
- 单项目单 Mod；
- 单项目多 Mod；
- 多 suite；
- 多项目逐子项目应用；
- Plugin 在 ModDev 前/后应用；
- 无 ModDev/无 Java/ModDev 1.x 友好失败；
- bench 能引用 Minecraft、NeoForge 和 main classes；
- main 看不到 bench classes；
- runtime dependency 仅进入 bench classpath；
- loadedMods、sourceSet、gameDirectory、namespace 正确；
- Gradle properties 白名单、Unicode、Windows 路径、secret 不泄露；
- collector 在正常/失败/无报告情况下运行；
- configuration cache 连续两次复用；
- 生产 JAR 无 Bench 内容。

快速 PR 测：help/tasks/compileBenchJava/processBenchResources/dry-run/configuration cache/collector fake fixtures。

Nightly/release 测：真正启动最小 GameTest server，验证 Runtime、target main、Provider、报告和自动退出；再做受控 client smoke。

## 14. CI 与性能回归策略

PR 阶段只以 crash、timeout、schema、workload correctness、宽松绝对预算为门禁。

Nightly 使用固定 self-hosted runner：

- baseline/candidate 同机交错；
- 多个全新 JVM fork；
- 固定电源模式与环境；
- server/client 分 job；
- 保存原始报告；
- 使用稳健统计；
- 相对变化和绝对变化同时超过阈值才失败；
- 样本不足为 `INCONCLUSIVE`。

共享 GitHub runner 不用于声称小幅性能变化。

## 15. 分阶段实施路线

### Phase 0：ADR 与接口原型

- 决定仓库名、group/plugin ID、版本策略、支持矩阵；
- 编写 Provider SPI ADR、报告 schema ADR、ModDev 边界 ADR；
- 用纯 Java 原型验证 ServiceLoader、场景状态机和 schema。

### Phase 1：Server MVP

- 创建 core API、NeoForge API、Runtime、Gradle plugin；
- 自动创建 bench source set与server run；
- 实现 ServiceLoader、生命周期、tick/JVM采样、JSON、timeout、自动停服；
- Super Lead 提供一个试点 Provider；
- 端到端 smoke 与生产 JAR 隔离检查。

### Phase 2：可靠性与 GameTest

- partial report、thread dump、artifact collector；
- Provider兼容诊断、tags/filter/parameters；
- GameTest bridge；
- JUnit XML；
- configuration cache/TestKit矩阵。

### Phase 3：Client 自动化

- 虚空世界、自动进入、相机轨迹；
- frame sampler、窗口有效性、生产 diagnostics facade；
- JSONL、Markdown、JFR、自动退出；
- Super Lead LOD/mesh/physics/attachment/picking 场景。

### Phase 4：A/B 回归

- baseline checkout；
- 多 JVM fork 和交错运行；
- robust statistics；
- PR summary；
- 宽松门禁与趋势报告。

### Phase 3.5：Dedicated + Separate Client 与网络故障注入

- 新增 paired server/remote-client ModDev runs，现有 integrated client 语义保持不变；
- 外层 coordinator 生成 session/nonce/端口，监督两个 Gradle invocation、网络 backend 与完整进程树；
- Runtime payload 握手校验 session、版本、目标 Mod、seed 与 paired scenario 摘要；
- server 作为 phase authority，两端通过非阻塞 barrier 进入 setup/warmup/measure/verify/teardown；
- 默认 TCP stream proxy 支持上下行固定延迟、确定性抖动、带宽限制、短时转发暂停与已有连接的 abortive 断连；blackhole、half-close 和 TCP handshake refusal 仅由显式宣告相应 capability 的 backend 提供；
- application-message 与 IP-packet fault 使用独立语义层，TCP stream backend 禁止伪造 loss/reorder/duplicate；
- 输出 server/client participant reports、paired summary、network profile 与 backend event JSONL；
- Windows 无管理员双 JVM E2E 为 required path，`tc netem`/WinDivert 为特权 opt-in backend。

### Phase 5：生态化

- 正式 adapter mod 模式；
- 可选 IMC；
- provider catalog/BOM；
- AE2/Mekanism 等标准场景包；
- dashboard 与高级 JFR 分析。

## 16. Super Lead 接入边界

Super Lead 已可复用：

- `SuperLeadNetwork.connect(...)`
- `SuperLeadNetwork.removeConnectionsWithoutDrops(...)`
- `SuperLeadNetwork.connections(...)`
- `SuperLeadNetwork.findConnectionById(...)`
- `RopePhysicsDiagnostics.snapshot()`
- `RopePhysicsDiagnostics.historySummary()`
- `StaticRopeChunkRegistry` 现有公开聚合 getter

正式 Mod 只补 immutable 只读 facade：

- `RopeDebugStats.snapshot()`
- `LeashBuilder.statsSnapshot()` 或统一纳入 DebugStats snapshot
- `StaticRopeChunkRegistry.snapshotState()`
- `RopeClientDiagnostics.clientConnections()`
- `RopeClientDiagnostics.frameSnapshot()`（包含 detached `RopePickingFrame`）
- 可选只读 `RopeSimulationSnapshot`

绝不能公开：

- live `RopeSimulation`；
- async job/Future/executor；
- 客户端缓存写入口；
- SavedData 原始写接口；
- static registry内部集合与驱动方法；
- render cache 修改方法；
- reset/clear 等可变统计控制面。

## 17. 验收标准

Server MVP 完成时必须满足：

1. 消费方只应用插件、添加 `src/bench` Provider即可生成 run。
2. Runtime 自动发现预期 Provider，0/重复/不兼容 Provider 给出明确失败。
3. 场景完整走 setup/stabilize/warmup/measure/verify/teardown。
4. 超时/异常仍产出 partial report、日志和退出状态。
5. 报告通过 schema 验证且包含环境、版本、workload正确性。
6. 生产 JAR、sources JAR、publication不含任何 bench 内容。
7. 普通 run 不加载 Runtime或Provider。
8. configuration cache 可复用。
9. 一个真实 Super Lead 服务端场景端到端通过。

Client 阶段完成时追加：

1. 启动后无需玩家输入，自动进入专用世界。
2. 真实服务端权威场景同步完成后才采样。
3. 玩家固定、镜头连续、一镜到底。
4. 失焦/暂停/resize等导致 run invalid，而不是生成误导数据。
5. 自动输出 JSONL/JSON/Markdown/JFR/log并正常退出或keepOpen。
6. LOD、static mesh、physics、stack、collision、attachment、picking均有真实生产路径场景。

## 18. 最终决策

- 工具链从 Super Lead 仓库独立出来，作为通用 ModBench 项目。
- 采用 Runtime Mod + Gradle Plugin + Provider SPI 三层架构。
- 默认 Provider 使用 ServiceLoader 和 test-only `src/bench`。
- 需要注册游戏内容时才升级为独立 Adapter Mod。
- Gradle 插件只使用 ModDev公开 DSL，首版支持 ModDev 2.x。
- 第一版先完成 dedicated Server MVP，验证工具链闭环；客户端一镜到底作为下一阶段。
- JSON 是权威结果，JUnit XML/Markdown是派生视图。
- 性能测试必须同时验证 workload正确性，防止“没干活所以更快”。