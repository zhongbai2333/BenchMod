# ModBench 实现进度

> 评估日期：2026-07-29。进度按 `docs/mod_bench_implementation_plan.md` 的可验证交付物计算，不以文件数量代替完成度。

## 总体判断

| 阶段 | 状态 | 当前结论 |
| --- | --- | --- |
| Phase 0：ADR 与接口原型 | 已完成 | Core SPI、ServiceLoader、同步生命周期原型、Schema 1.0.0 与关键 ADR 已落地并有测试。 |
| Phase 1：Server MVP | 纵切可运行，剩真实业务试点 | 独立消费方已通过真实 dedicated server E2E；报告含完整环境/版本/loadedMods、per-phase 计时与 tick/自定义指标统计；Plugin 自动注册验证/清理/收集任务；仅剩真实 Super Lead 试点。 |
| Phase 2：可靠性与 GameTest | 部分提前实现 | 已有 partial report、Provider 兼容诊断、configuration cache 验证、timeout thread dump、artifact collector、失败产物 bundle、参数/过滤和正式 Schema 验证；GameTest、JUnit XML 与完整 TestKit 矩阵未完成。 |
| Phase 3：Client 自动化 | 基本完成，GUI 第一纵切 | Client Provider/Context、client tick runner、frame sampler、自动 integrated world、图形基线、相机与构图、渲染/环境门禁、截图与像素比对、JFR/样本/Markdown、GUI interaction-tree 快照、strict selector、expected-Screen scope 和 selector 驱动控件区域截图已实现。普通 client 自动化已有真实 E2E；GUI 调试仍缺外部消费方真实 E2E、自动输入、tooltip/完整视觉树与失败 evidence group。 |
| Phase 3.5：双端专服与网络模拟 | 数据面原型 | paired orchestration 与网络语义 ADR 已接受；`bench-network-core` 已提供方向性 profile、能力协商、规范化 hash 和确定性 seed；`bench-network-proxy` 已实现 fixed-quantum + idle flush 的双向 TCP 转发、延迟/抖动/带宽/pause/abort、指标与 schema 化 JSONL 事件，并有 Windows loopback 回归测试。separate client、coordinator、paired report 与双 JVM Minecraft E2E 尚未实现。 |
| Phase 4：A/B 回归 | 未开始 | Baseline/candidate、多 JVM fork、稳健统计和 PR summary 尚未实现。 |
| Phase 5：生态化 | 未开始 | Adapter Mod、catalog/BOM、标准场景包和 dashboard 尚未实现。 |

项目当前适合描述为：**Server MVP 与 integrated-client 技术纵切已经跑通，处于 Phase 1 真实业务验收收尾，并已完成较多 Phase 2 可靠性与 Phase 3 客户端能力。**

## 已实现能力

### 构建与模块

- Gradle 9.5.1 Wrapper、Java 25、多模块构建与集中版本属性；当前验证线为 Minecraft 26.1.2 / NeoForge 26.1.2.76。
- `bench-api-core`、`bench-api-neoforge-26.1`、`bench-runtime-neoforge-26.1`、`bench-gradle-plugin`、`bench-report-schema`。
- `bench-network-core`：与 Minecraft 解耦的网络 profile、语义层、backend capability、稳定 profile hash 与独立随机流派生；TCP stream profile 会拒绝伪 loss/reorder/duplicate。
- `bench-network-proxy`：Java 25 虚拟线程实现的跨平台 loopback TCP data plane；在线小消息通过 5 ms idle flush 有界转发，完整 quantum 的 jitter 轨迹不依赖 socket read 分区；支持方向性延迟、确定性抖动、token-bucket 带宽、可重叠 pause 与 abortive close，并输出 metrics 和 `modbench-network-event-1` JSONL。默认 capability 明确不包含 blackhole、half-close、handshake refusal 或 packet loss。
- 可独立运行的 `examples/simple-neoforge-mod` 消费方，而不是仅在 Runtime 项目内自测。

### Core 与 Provider

- Provider SPI、兼容范围、稳定 ID、场景 descriptor 与状态枚举。
- `ServiceLoader<BenchProvider>` 发现、排序、重复 ID、非法元数据、预期数量和 `ServiceConfigurationError` 诊断。
- Runtime 显式使用游戏 classloader，避免 FML thread context classloader 冲突。

### Dedicated server Runtime

- NeoForge Runtime `@Mod` 和 server started/tick/stopping 事件适配。
- setup、stabilize、warmup、measure、verify、teardown 的跨 tick 状态机。
- timeout、cancellation、异常和 `AssertionError` 状态转换；失败路径仍执行一次 teardown。
- `ServerStoppingEvent` 中止活动场景时会先同步驱动 cancellation/teardown，再写入场景结果并 finalize；run 保持 `ABORTED`，且有 teardown-once 回归测试。
- JVM heap/non-heap、GC、线程与 tick mean 基础诊断。
- partial/final JSON 原子写入、固定日志 marker 与完成后自动停服。
- 场景 TIMED_OUT 时写全线程 jstack 风格 dump（含 monitor/synchronizer/死锁检测）到 `artifacts/thread-dumps/<scenario>.txt` 并登记为 report artifact；server 与 client 引擎共用，已用注入挂起的真实 E2E 验证。

### 试点反馈驱动的 API（server + client 共用）

- `ScenarioDescriptor.phaseTimeout` 现在真实生效：每场景 phase 预算取 `max(全局 phaseTimeoutTicks, 声明值)`——长测量场景不再需要调大全局超时（Super Lead 试点的第一痛点）。
- `context.artifacts()`（`BenchArtifactWriter`）：场景可 `write("trace.csv", "text/csv", content)` 到 `artifacts/custom/` 或 `register(...)` 已生成文件，产物带 SHA-256/字节数进报告、可被验收、随 bundle 收集；文件名有穿越防护，register 限制在结果目录内（消灭裸写 resultDirectory 的 workaround）。
- 场景过滤：`modBench.scenarioFilter` DSL + `-PmodBench.scenarios=<ids>` 命令行覆盖（首个白名单转发属性），逗号分隔、尾部 `*` 前缀匹配；不匹配场景报 `SKIPPED`，受限过滤零匹配时 run 判 FAILED 并列出可用场景 id（防拼错静默空跑）。正反例均真实 E2E 验证。
- `BenchClientPose.lookingAt(x,y,z, tx,ty,tz)` 静态工厂：从站位直接构造看向目标的 pose（试点自写三角函数的替代）。

### 指标、phase 计时与环境（server + client 共用）

- `PhaseTimeline` 记录每个生命周期 phase 的起止 tick、真实 wall 时长与结局（completed/failed/timed_out/aborted），进入报告 `scenarios[].phases`。
- 固定容量、热路径零分配的 `MetricAccumulator` + `ScenarioMetricCollector` 按 metric×phase 聚合；输出 count/dropped/min/max/mean/median/P90/P95/P99/stdDev 到 `scenarios[].metrics`。
- 内建指标：`server.tick.duration`（ServerTickEvent Pre→Post，按 phase 归档）与 `client.frame.interval`（按 phase 归档）；client MEASURE 帧指标额外带 1% low、0.1% low 与 >16.67/33.33/50/100ms 超预算帧数。
- Provider 通过 `context.metrics().record(descriptor, value)` 记录的自定义指标进入当前 phase 的 metrics 数组，不再只是 diagnostics 字符串。
- 报告 `environment` 现含 OS 名称/版本/架构、逻辑核数、最大堆、Java vendor/VM、脱敏后的 JVM args、Minecraft/NeoForge 版本（`FMLLoader.getCurrent().getVersionInfo()`）与完整 `loadedMods`（`ModList`，含版本）。
- `environment.git` 记录本地 commit 与 dirty：Plugin 用 configuration-cache 安全的 `GitStateValueSource` 执行 `git rev-parse HEAD` / `git status --porcelain` 并经系统属性转发；非 git 检出安静降级；CI 下另记 `ci.runId`，`GITHUB_SHA` 作为 commit 兜底。
- `run.parameters` 记录 phaseTimeoutTicks、expectedProviderCount、client 图形基线与帧稳定/门禁配置，数值/布尔按原生类型序列化，报告自身即可复现实验条件。
- 每个场景的原始样本写入 `artifacts/samples/<scenario>.jsonl`（每 metric×phase 一行，含 dropped 与完整 values 数组），登记为 `samples` artifact；离线工具可重算分布或逐样本对比两次运行。
- `BenchImageDiff.compare(a, b)`：纯 Java 像素比对（每通道容差 4 吸收编解码噪声，输出 differing ratio 与 MAE），场景可断言相机确实移动或画面保持稳定；示例场景以 `simplebench.screenshot.diff_ratio` 指标入报告（实测两停靠点 diff 0.80）。
- `jfrEnabled = true` 时两端引擎按实际执行场景自动启停 Flight Recorder（JDK `default` 低开销配置，约 1%），每个 `artifacts/jfr/<scenario-id>.jfr` 独立登记为 artifact；setup 失败、timeout 和 abort 同样尽量保留当前场景录制。录制文件已有 FLR magic、非空和顺序重启测试，消费方 E2E 验证文件进入报告与 bundle。
- `writeFinal` 同时生成派生视图 `report.md`：run/环境头、场景表、phase 计时表、指标表（ns 自动换算 ms）与 artifact 清单；JSON 仍是权威格式，Markdown 写失败不影响结果。
- 帧稳定判据（`clientStableFrameRatio`，默认 2.0）与截图门禁预算（`clientCaptureGateFrameBudget`，默认 900 帧）现可经 DSL 配置并记录在 run.parameters。
- Runtime jar manifest 写入 `Implementation-Version`，`modbench_runtime` 在 loadedMods 中显示真实版本而非 `0.0NONE`。
- `VerifyBenchReportTask` 解析 JSON 并通过内置 Draft 2020-12 Schema 后，再验收 status、runType、场景、artifact、diagnostic、指标与 loadedMods；无效 status 等结构错误有 TestKit 负例。
- 报告 `environment.versions` 记录 Plugin、Core API、NeoForge API、Runtime 与 Schema 版本；Plugin 启动参数提供统一版本，Runtime 可从 JAR `Implementation-Version` 回退，不再硬编码版本。

### Integrated client 基础纵切

- `BenchClientProvider`、`BenchClientContext`、client scenario/factory/registrar 和共享 NeoForge Provider 基接口。
- client tick 驱动的 setup/stabilize/warmup/measure/verify/teardown runner，含 timeout、cancellation 与 teardown-once 语义。
- physical-client-only `ClientTickEvent.Post` 和 `RenderFrameEvent.Pre` 适配，不污染 dedicated server classloading。
- 固定容量、frame 热路径无分配的 interval sampler，输出 sample/drop/mean/P95/P99/max 基础诊断。
- Plugin 自动创建隔离的 `runBenchClient`、client game directory 和 result directory。
- Runtime-owned `BenchClientAutomation` 支持绝对玩家位置/朝向、插值移动、look-at、停止输入/速度、HUD 显隐和 PNG 截图；截图异步落盘并带 SHA-256 与字节数登记为 report artifact。
- `BenchGuiSession` 提供 scenario-scoped expected-Screen 授权、按 live listener identity 注册稳定语义名、detached interaction-tree 快照、0/1/ambiguous/nth strict selector，以及 render-post 同帧重校验后的控件区域截图；GUI scale 以四边 floor/ceil 映射到 framebuffer 并 clamp。该树仅覆盖 `GuiEventListener`/`ContainerEventHandler`，不声称包含纯 `Renderable` 视觉元素。
- `BenchCameraFramer` 根据 world-space `BenchBounds3`、观察方向、实际视口宽高比和 FOV 求解完整 bounds 入镜的 eye pose；`frameTarget` 自动补偿玩家眼高，`holdFramedTarget`/`holdPose` 在每个 client tick 重施机位并在场景边界释放，避免高空截图受重力漂移。权威 PNG 保留原始 framebuffer，不做事后放大裁剪。
- `BenchCameraPath` 关键帧时间轴：线性/缓入/缓出/缓入缓出/smooth-step 插值、按 tick 或按每秒方块数的固定速度、`hold` 静止段、逐关键帧自动截图、单次/循环/往返模式、逐帧插值与逐 tick 快照两种应用方式。采样是纯函数，可脱离 Minecraft 单元测试。
- `BenchCameraPlayback` 在截图落盘前暂停时间轴并锁定当前 pose，播放结束后继续保持最后一个关键帧，保证截图与关键帧一一对应且播放后不会漂移。
- `ClientReadinessGate` 要求资源加载完成、无遮挡屏幕/overlay、区块已加载、chunk mesh 队列为空且已有可见 section；`FrameStabilityMonitor` 在固定 32 帧窗口内判定最新若干帧间隔是否可比。
- `BenchCaptureOptions` 默认在截图前同时满足渲染就绪与连续稳定帧，并隐藏 HUD；门禁超过 900 帧预算仍未打开时照常截图，但记录 `gate_satisfied=false` 并使整轮失效，不会挂起。
- `ClientEnvironmentGuard` 在渲染首次就绪后武装，随后监测窗口尺寸变化、最小化、失焦、暂停、弹出屏幕与 overlay；任一命中即记录 invalidation 并把整轮报告降级为 `INCONCLUSIVE`。失焦严格度由 `clientRequireWindowFocus` 控制。
- 图形基线关闭 `pauseOnLostFocus`，无人值守运行不会被失焦弹出的暂停菜单卡住。
- 图形基线把 `inactivityFpsLimit` 固定为 `MINIMIZED`：Minecraft 的 AFK 降帧（无真实输入 60 秒后降到 30fps、10 分钟后 10fps）对无人值守 bench 必然触发，会静默污染帧指标；关闭后未聚焦但可见的窗口保持满速渲染。窗口最小化仍会被引擎降到 10fps，与环境守卫的 `INCONCLUSIVE` 判定自洽。
- Runtime 每个 client tick 释放鼠标抓取：光标不再被游戏窗口锁定，且物理鼠标移动无法转动玩家视角（本身是不确定性来源）。两项均记入 `client.graphics.*` 诊断并被示例 E2E 验收。
- client 场景失败、超时或取消时自动抓取最终画面 `failure-<scenario>.png`，并在场景边界为未完成截图预留落盘预算。
- 使用 Minecraft 26.1.2 `WorldOpenFlows` 自动创建或打开固定 ID/seed 的 integrated world，包含 world-ready timeout。
- Client 世界预设 `clientWorldPreset`：`normal` / `flat` / `void`（vanilla "The Void" superflat，地形负载归零）；非默认预设自动使用带后缀的世界目录，切换预设不会复用旧生成器的世界。三种预设均经真实 E2E 验证（void 截图确认纯虚空画面）。
- Client 维度 `clientDimension`：`overworld` / `the_nether` / `the_end`；integrated server 侧传送，等待 client level 切换与新 LocalPlayer 就绪后才开始场景，超时归入 dimension_ready 失败。the_end 已经真实 E2E 验证（截图含 Ender Dragon 与黑曜石柱）。
- Dedicated server 世界供给：`prepareBenchServerWorld` 在每次 `runBenchServer` 前把 `level-seed`（=`modBench.seed`）、`level-type`（`serverLevelType`）与可选 `generator-settings` 写入 server.properties，供给指纹变化时自动删除旧世界目录——报告里的 seed 从此真实对应服务端世界。`minecraft:flat` 已经真实 E2E 验证，`serverLevelType` 记入 run.parameters。
- `clientWorldPreset`（normal/flat/void）选择主世界生成：void 为原版 The Void 超平坦（`FlatLevelGeneratorPresets.THE_VOID` 替换 overworld generator），实测 rendered sections 从 158 降到 6；非默认 preset 世界目录追加后缀，切换 preset 不会复用旧生成。
- `clientDimension`（overworld/the_nether/the_end）在场景开始前经 integrated server `teleportTo` 把玩家放入目标维度，带重试与 dimension-ready 超时；末地实测截图含末地石、黑曜石柱与末影水晶，报告记录实际维度。
- `prepareBenchServerWorld`（Plugin 自动注册，`runBenchServer` 前置）把 `level-seed`（取 `modBench.seed`）、`serverLevelType` 与可选 `serverGeneratorSettings` 合并进 server.properties，provisioning 变更时自动删除世界目录——修复了 dedicated server 世界种子实际随机、与报告 seed 不符的问题。
- 应用并记录窗口大小、窗口化、VSync、FPS cap、render distance 与 simulation distance 基线。
- 独立示例双端 Provider 已通过无人值守真实 client E2E：关键帧相机路径、三张 1280×720 有效画面截图（两张带 HUD、一张纯渲染）、`client.environment.valid=true` 与 `gate_satisfied=true` 诊断均已验收，正式 Schema validator 通过；注入失败后 `failure-simplebench-client-render-smoke.png` 也已实测生成。

### Gradle Plugin 与隔离

- 自动创建默认 `bench` source set 和 ModDev `benchServer` run。
- 通过公开 ModDev DSL 绑定 target Mod transformed source folders。
- `benchRuntimeMod` 仅进入 bench runtime classpath，不进入普通 `runtimeClasspath`。
- 全部模块（core/neoforge API、Runtime、Gradle plugin 含 marker、report schema）可 `publishToMavenLocal`；POM 无 Minecraft/NeoForge 泄漏；独立示例已改为纯 mavenLocal 消费（plugin marker 解析 + 坐标依赖），configuration cache 复用已验证。
- 插件按自身版本自动注入 `benchImplementation`（core + neoforge API）与 `benchRuntimeMod`（Runtime，非传递）；`modBench.automaticDependencies = false` 可退出；消费方零依赖声明即可接入。
- `verifyProductionJarHasNoBenchContent` 同时校验 sources JAR（对照 bench 源码目录），发布物隔离纳入 `check`。
- 外部接入步骤文档：`docs/consumer-quickstart.md`。
- Plugin 自动注册通用任务：`verifyProductionJarHasNoBenchContent`（挂入 `check`，检查 bench source set 输出重叠、ModBench 包泄漏与 ServiceLoader descriptor，不再含示例特定判断）、`verifyBenchServerReport`/`verifyBenchClientReport`（默认报告路径与 runType，消费方只补场景/指标期望）、`cleanBenchServerResults`/`cleanBenchClientResults`（`runBench*` 前自动清理旧结果）、`collectBenchServerArtifacts`/`collectBenchClientArtifacts` + 聚合 `collectBenchArtifacts`（`runBench*` 被 finalizedBy，失败也把报告/日志/crash-reports 收进 `build/modBench/bundles/<suite>/<runType>` 并写 manifest.json）。
- 示例生产 JAR 不包含 Provider 或 ServiceLoader descriptor；TestKit 覆盖 descriptor 泄漏的负例。
- TestKit 消费方形状矩阵（真实 ModDev 2.0.141 经共享 plugin-under-test classpath 参与）：ModBench 在 ModDev 之前/之后应用均生成 `runBench*`；多 Mod 项目缺 `targetMod` 给出明确失败、指定后正常；Groovy DSL 消费方可编译并通过生产 JAR 检查；Unicode（中文）项目路径可编译验证；`benchRuntimeMod` 不泄漏进普通 `runtimeClasspath` 且确实进入 bench runtime classpath；无 java 插件时安静不作为而非崩溃。

### 报告与测试

- Draft 2020-12 Schema `1.0.0`、最小样例、正负 schema 测试。
- Core discovery/lifecycle、Runtime runner 和 Plugin TestKit 基础测试。
- `BenchCameraPath` 采样、缓动曲线、最短弧旋转、固定速度换算、循环/往返映射与关键帧查找的纯函数测试；`CameraPathPlayer` 逐 tick 应用、截图暂停、播放后保持与循环只截一次的行为测试；`FrameStabilityMonitor` 窗口与卡顿判定测试；GUI rectangle、嵌套 interaction tree selector、歧义/nth、visible/active 过滤及非整数/边缘/极值 framebuffer 坐标映射测试。
- Runtime 内部 smoke 与外部消费方真实 dedicated server E2E。
- 当前已验证基线：根项目 `check` 成功；外部示例缓存复用、Runtime classpath 隔离、生产 JAR 隔离和 `PASSED` 报告均成功。

## Server MVP 验收矩阵

| # | 验收标准 | 状态 | 证据或缺口 |
| --- | --- | --- | --- |
| 1 | 应用插件并添加 `src/bench` Provider 即生成 run | 已完成 | Plugin 自动创建 source set、runs，并按自身版本自动注入 API/Runtime 依赖；消费方零依赖声明。 |
| 2 | 0/重复/不兼容 Provider 明确失败 | 已完成 | Core discovery 与 Runtime registry 有数量、重复和兼容性诊断及测试。 |
| 3 | 完整生命周期 | 已完成 | Runtime runner 和真实示例覆盖 setup 至 teardown。 |
| 4 | 超时/异常仍产出 partial、日志和退出状态 | 已完成 | partial、marker、状态与停服已有；timeout thread dump 与 `collectBench*Artifacts` 失败产物 bundle 已实现并经注入挂起 E2E 验证。 |
| 5 | Schema 正确且含环境、版本、workload correctness | 已完成 | Schema 校验、correctness、loadedMods、Minecraft/NeoForge 版本、OS/CPU/内存/JVM args、本地 git commit/dirty 与 CI 身份均已写入报告。 |
| 6 | JAR、sources JAR、publication 均无 bench 内容 | 已完成 | 生产 JAR 与 sources JAR 检查通用化并自动挂入 `check`（javadoc JAR 未单独检查，内容源自 main sources）。 |
| 7 | 普通 run 不加载 Runtime 或 Provider | 已完成 | 普通 `runtimeClasspath` 与生产 JAR 隔离已验证；仍应扩展 TestKit 矩阵覆盖普通 ModDev runs。 |
| 8 | configuration cache 可复用 | 已完成 | 根构建和独立示例连续运行已验证复用。 |
| 9 | 一个真实 Super Lead 服务端场景 E2E | 未完成 | 当前只有通用 smoke target；尚未接入 Super Lead 真实业务 workload。 |

## 当前技术债与风险

1. Plugin 仍是单一默认 suite，不是计划中的 `NamedDomainObjectContainer<BenchSuiteSpec>` 多 suite DSL；client 结果目录在 Plugin 与 ModDevConfigurer 中重复推导。
2. 单 tick 内的真死锁（场景阻塞 server thread）只能靠 Minecraft watchdog，Runtime 的 timeout dump 覆盖的是跨 tick 挂起。
3. `report.md` 是唯一派生视图，JUnit XML 未实现；截图比对仅为像素级（无感知哈希/SSIM）。
4. 仅发布到 mavenLocal，尚无远程 Maven 仓库；javadoc JAR、POM/module metadata 与普通 ModDev run 的完整隔离矩阵仍待加强。
5. TestKit 已覆盖 Groovy DSL、多 Mod、应用顺序、Unicode 路径、首个白名单 `-PmodBench.scenarios` 转发与 classpath 隔离；仍缺多 suite、多项目逐子项目应用、更多白名单属性与 ModDev 1.x 友好失败。
6. JFR 已实现，GameTest 尚未实现；单 tick 内阻塞和 GameTest bridge 仍是下一阶段可靠性重点。

## 建议下一里程碑

按风险和依赖顺序推进：

1. 完成 dedicated server + separate client passthrough paired run、session/nonce 握手和联合退出。
2. 把现有无管理员 TCP data plane 接入 coordinator，登记 network profile、metrics 与 backend event artifacts，并完成异常退出/残留线程清理。
3. 增加 paired/report schema、Windows 双 JVM Minecraft E2E；随后再接 `tc netem`/WinDivert 等真实 packet backend。

## 项目 AI 定制

- 消费方适配 Skill：`.github/skills/adapt-neoforge-mod/SKILL.md`
- 日常维护 Agent：`.github/agents/modbench-maintainer.agent.md`
- 独立发布审计 Agent：`.github/agents/modbench-release-auditor.agent.md`

Skill 负责告诉 AI 如何把其他 NeoForge Mod 接入本项目；Maintainer Agent 负责实现、修复和演进；Release Auditor 只读验证跨模块边界与发布门禁。