# ModBench

ModBench 是面向 NeoForge Mod 的可复用游戏内基准测试工具链。它将构建期编排、游戏内执行和业务 workload 分成 Gradle Plugin、Runtime Mod 与 test-only Provider 三层。

## 当前状态

项目已完成 **Phase 0**，并已跑通 **Phase 1（Server MVP）技术纵切**；当前处于 Phase 1 真实业务验收收尾，同时已完成较多 Phase 2 可靠性与 Phase 3 integrated-client 能力：

- `bench-api-core`：纯 Java Provider SPI、兼容范围、ServiceLoader 发现和生命周期原型；
- `bench-report-schema`：权威 JSON 报告 Schema `1.0.0` 与最小样例；
- `bench-api-neoforge-26.1`：非阻塞、跨 tick 的 dedicated server 场景契约；
- `bench-runtime-neoforge-26.1`：server/client lifecycle runner、JVM/GC 与客户端 frame interval 基础采样；
- `bench-gradle-plugin`：隔离 `bench` source set，并通过 ModDev 2.x 公共 DSL创建 `benchServer` 与 `benchClient` run；
- `examples/simple-neoforge-mod`：独立消费方示例，验证 Plugin、`src/bench` Provider 和 Runtime 隔离；
- `docs/adr`：Provider、报告和 ModDev 公共边界的架构决策记录。

当前 Server MVP 纵切已针对 NeoForge `26.1.2.76` / Minecraft `26.1.2` dedicated server 配置并验证：Runtime `@Mod` 启动、游戏 classloader ServiceLoader、跨 tick workload、partial/final JSON 原子写入、自动停服和 Draft 2020-12 Schema 验证均已闭环。

客户端纵切现已具备 `BenchClientProvider`、client tick 生命周期、`RenderFrameEvent.Pre` 帧间隔采样、关键帧 camera 时间轴（缓动、固定速度、循环/往返）、渲染就绪与帧稳定门禁、环境有效性判定、可隐藏 HUD 的 PNG 截图（带 SHA-256）、GUI interaction-tree 快照/严格 selector/点击、滚动、拖动、按键、Unicode 输入/控件区域截图、失败时自动抓帧、client 报告和 `runBenchClient`。Runtime 会自动创建或重用固定 seed 的 integrated world，应用窗口、VSync、FPS cap 与视距基线，执行场景并自动退出；普通 client 自动化的独立消费方真实 E2E 已通过，GUI 交互仍待外部消费方真实 E2E。

尚待完成的客户端能力包括 tooltip 与完整视觉树、感知级截图比对、目标投影门禁和更多真实 Mod 验证。

下一条客户端纵切是 `dedicated server + separate client` paired 模式，用于覆盖 integrated world 无法重现的登录、服务端权威同步、真实 socket 与断连问题。该模式将支持可复现网络 profile：默认无管理员跨平台 TCP proxy 提供方向性延迟、抖动、带宽、转发卡顿和连接中止；真正 packet loss/reorder/duplicate 仅由明确标记的 IP-packet backend 提供。TCP proxy 不会用“随机丢字节”伪装丢包。

其他待完成项包括：更严格的 phase/metric/artifact Schema 约束、GameTest 与 JUnit XML、多 suite DSL、paired 双 JVM 编排、A/B 回归，以及真实 SuperLead 业务场景验收。Plugin 报告任务现会解析 JSON、执行正式 Draft 2020-12 Schema 验证，再检查场景、指标、artifact、diagnostic 与 loaded Mod 期望。

逐阶段完成度、Server MVP 验收矩阵与下一里程碑见 [`docs/implementation_status.md`](docs/implementation_status.md)。

## 构建

要求 Java 25。仓库使用 Gradle Wrapper：

```powershell
.\gradlew.bat check
```

完整设计与验收标准见 [`docs/mod_bench_implementation_plan.md`](docs/mod_bench_implementation_plan.md)。

准备让真实 NeoForge Mod（例如 SuperLead）接入时，先阅读 [`docs/superlead-adoption-readiness.md`](docs/superlead-adoption-readiness.md)，确认版本线、Provider 业务 API、就绪判定、精确清理和 dedicated-server 验收门禁。

### Dedicated server smoke

```powershell
.\gradlew.bat :bench-runtime-neoforge-26.1:runBenchServer
```

该任务无需玩家输入，会启动真实 dedicated server、执行 test-only `SmokeBenchProvider` 并自动退出。权威报告输出到：

`bench-runtime-neoforge-26.1/build/modBench/raw-results/smoke/server/summary.json`

生产 Runtime JAR 不包含 smoke Provider 或 `META-INF/services/BenchProvider`。

独立消费方示例位于 [`examples/simple-neoforge-mod`](examples/simple-neoforge-mod)。其 `check` 验证生产 JAR 隔离，`verifyBenchServer` 启动真实 dedicated server 并验收报告。

报告不再只有状态：`scenarios[].phases` 记录每个生命周期 phase 的起止 tick、wall 时长和结局；`scenarios[].metrics` 记录内建 `server.tick.duration` / `client.frame.interval` 和 Provider 经 `context.metrics().record(...)` 提交的自定义指标，含 count/min/max/mean/median/P90/P95/P99/stdDev（client MEASURE 帧指标另带 1% low、0.1% low 与超预算帧数）；`environment` 含 OS/CPU/内存/Java/JVM args（敏感参数脱敏）、Minecraft/NeoForge 版本与完整 `loadedMods`；`run.parameters` 记录超时与图形基线。`VerifyBenchReportTask` 可用 `expectedMetricNames` 和 `expectedLoadedModIds` 验收这些内容。

Plugin 自动注册整套验证与收集任务：`verifyProductionJarHasNoBenchContent` 挂入 `check`（检查 bench source set 输出重叠、ModBench 包泄漏与 ServiceLoader descriptor）；`verifyBenchServer` / `verifyBenchClient` 组合真实 run 与报告验收，底层 `verifyBench*Report` 预设默认报告路径与 runType，消费方只需补场景与指标期望；`runBench*` 之前自动清理旧结果、之后无论成败都会被 `collectBench*Artifacts` finalize，把报告、游戏日志和 crash-reports 打包进 `build/modBench/bundles/<suite>/<runType>` 并写 manifest。场景超时时 Runtime 会写全线程 dump 到 `artifacts/thread-dumps/<scenario>.txt` 并登记为 artifact。

每个场景的原始指标样本导出为 `artifacts/samples/<scenario>.jsonl`（每 metric×phase 一行），`summary.json` 旁另生成人类可读的 `report.md` 派生视图。`environment.git` 经 configuration-cache 安全的 git 探测记录本地 commit 与 dirty 状态。帧稳定判据与截图门禁预算可经 `clientStableFrameRatio` / `clientCaptureGateFrameBudget` 配置，全部实验参数都在 `run.parameters` 里。

正式版本通过 **JitPack** 消费，插件用 `resolutionStrategy.useModule(...)` 映射到 `bench-gradle-plugin`，API/Runtime 依赖由插件按同一次 JitPack 构建的坐标自动注入（可用 `modBench.automaticDependencies = false` 关闭）。仓库内示例继续用 `mavenLocal` 验证开发中的快照。外部 Mod 的完整接入步骤见 [docs/consumer-quickstart.md](docs/consumer-quickstart.md)，发布流程见 [docs/releasing.md](docs/releasing.md)。

### Client MVP

Plugin 会为同时实现 `BenchClientProvider` 的 `src/bench` Provider 生成 `runBenchClient`。Runtime 自动创建或打开 `modbench-client-world`，世界和玩家可用后执行 client 场景、采集 frame interval、写入 `build/modBench/raw-results/default/client/summary.json` 并退出。

Client Provider 可通过 `context.automation()` 调用 `setPose(...)`、`movePose(...)`、`lookAt(...)`、`stopMovement()`、`setHudHidden(...)` 和 `captureScreenshot(...)`。截图请求返回 `CompletableFuture<Path>`，场景应在后续 client tick 非阻塞地检查完成状态；PNG 写入 `artifacts/screenshots`，并带 SHA-256 与字节数登记到报告 `artifacts`。

GUI 自动化使用 `automation().beginGuiSession(ExpectedScreen.class)`。会话把目标 Screen 视为预期环境，而不是误判为遮挡；可用 `name(widget, "stable-id")` 按对象 identity 注册场景内稳定名称，再通过 `snapshot()` 获取 detached `Screen`/`GuiEventListener` interaction tree，或用 `select(BenchGuiSelector.semanticName("stable-id"))` 严格选择节点。`await(...)` / `awaitMissing(...)` 可直接作为跨 tick step 返回值；`click(...)`、`doubleClick(...)`、`scroll(...)`、`drag(...)`、`pressKey(...)` 和 `typeText(...)` 沿 Minecraft/NeoForge Screen 输入链分发。0 匹配和越界会等待或失败，歧义绝不静默取第一个。`captureWidget(...)` 在 `RenderFrameEvent.Post` 对同一帧重新解析 selector，并按 GUI scale 裁剪控件区域到 `artifacts/gui`。这些 API 只能在 Minecraft client thread 调用，返回的 future 应跨 tick 非阻塞轮询；会话会在场景边界自动释放。

第一版快照是**交互树而非完整视觉树**：它遍历 `Screen.children()` 与嵌套 `ContainerEventHandler.children()`，不包含 `addRenderableOnly`、背景纹理、直接绘制文字等纯 `Renderable` 内容。tooltip 捕获和完整 accessibility/visual tree 仍属后续纵切。

需要突出业务对象时，不要手算固定相机距离、FOV 或玩家眼高。用 `BenchBounds3` 描述关键对象的 world-space 包围盒，再以 `BenchCameraFraming` 指定观察方向和 `frameFill`（例如 `0.8`），调用 `automation().frameTarget(...)` 自动构图。高空或长时间测量使用 `holdFramedTarget(...)`，返回的 `BenchPoseHold` 会跨 client tick 抵消重力和位置漂移；在 teardown 中 `release()`，场景结束时 Runtime 也会兜底释放。原始 framebuffer PNG 仍是权威 artifact，不会通过事后裁剪伪造细节。

连续相机运动使用 `BenchCameraPath`：从起始 pose 出发，用 `to(...)` 按 tick 数、或 `toAtSpeed(...)` 按每秒方块数追加关键帧，`hold(...)` 让画面静止，`capture(name)` 在该关键帧自动截图，`mode(...)` 选择单次/循环/往返，`snapped()` 关闭逐帧插值。`automation().playPath(path)` 返回 `BenchCameraPlayback`，场景每个 client tick 调用一次 `advance()`。截图未落盘前时间轴会暂停并锁定当前 pose，播放结束后继续保持最后一个关键帧，因此截图内容必定对应它所属的关键帧。

Bench 运行不锁定鼠标（每 tick 释放抓取，光标可自由离开窗口，物理鼠标也无法干扰玩家视角），并把 `inactivityFpsLimit` 固定为 `MINIMIZED` 关闭 AFK 降帧——未聚焦的后台窗口保持满速渲染，只有最小化仍会被引擎强制 10fps（此时环境守卫会把结果判为 `INCONCLUSIVE`）。

`BenchImageDiff.compare(a, b)` 提供像素级截图比对（带编解码容差的 differing ratio 与 MAE），场景可据此断言相机移动或画面稳定；`jfrEnabled = true` 时每次运行自动录制低开销 JFR 并把 `artifacts/jfr/recording.jfr` 登记进报告，失败运行同样保留录制。

世界可按基准需求供给：`clientWorldPreset`（`normal`/`flat`/`void`，虚空预设把地形负载归零）、`clientDimension`（`overworld`/`the_nether`/`the_end`，integrated server 侧传送后才开始场景）、`serverLevelType` + `serverGeneratorSettings`（写入 dedicated server 的 server.properties，`seed` 同时就是 `level-seed`）。供给指纹变化时旧世界自动重置，报告的 `run.parameters` 完整记录这些条件。

世界与维度可配置：`clientWorldPreset` 选主世界生成方式（`normal` / `flat` / `void`——虚空为原版 The Void 超平坦，只剩出生平台，地形负载归零，非默认 preset 使用独立世界目录避免复用旧生成）；`clientDimension` 决定玩家在场景开始前被放到哪个维度（`overworld` / `the_nether` / `the_end`，经 integrated server 传送并等待就绪门禁重新满足）。dedicated server 端由 `prepareBenchServerWorld` 把 `level-seed`（取 `modBench.seed`）与 `serverLevelType` 写入 server.properties，配置变更时自动重置世界——服务端世界种子从此与报告一致，不再是随机值。

`context.environment()` 暴露渲染就绪状态与环境有效性。`readiness()` 要求资源加载完成、无遮挡屏幕、区块已加载且 mesh 队列为空；`BenchCaptureOptions.defaults()` 会在截图前等待该状态与连续稳定帧，并可隐藏 HUD。窗口失焦、最小化、暂停、弹出屏幕或尺寸变化都会记入 invalidation，此时整轮报告为 `INCONCLUSIVE` 而不是 `PASSED`。失焦判定可用 `clientRequireWindowFocus` 关闭。场景失败时 Runtime 会自动抓取最终画面为 `failure-<scenario>.png`。

独立示例的 `verifyBenchClient` 会执行无人值守真实客户端 E2E，并验收场景状态、三个截图 artifact、非空 PNG，以及 `client.environment.valid=true`、`client.screenshot.gate_satisfied=true`、`client.screenshot.hud_hidden=true` 等诊断。

## AI 协作

仓库包含项目级 Copilot 定制：

- `adapt-neoforge-mod` Skill：指导 AI 将外部 NeoForge Mod 接入 ModBench；
- `ModBench Maintainer` Agent：负责日常实现、修复、测试和路线演进；
- `ModBench Release Auditor` Agent：以只读方式审计报告、classloader、Gradle cache 和产物隔离。

文件分别位于 `.github/skills` 与 `.github/agents`，可由 VS Code Copilot 自动发现或手动选择。

## 模块边界

- Core API 不依赖 Minecraft、NeoForge、Gradle 或 JSON 实现；
- Provider 通过 `ServiceLoader<BenchProvider>` 在游戏 classloader 中发现；
- Runtime 拥有状态机、调度、timeout、采样与报告写入；
- Provider 只声明场景和 workload hook，不创建线程、不控制全局执行；
- JSON 是权威产物，Markdown 已实现，JUnit XML 是后续派生视图。

## 许可证

[MIT](LICENSE)
