# 客户端自动化 / Client automation

[返回首页 / Home](Home.md) · [报告与证据 / Reports](Reports-and-Comparison.md)

## 运行模型 / Execution models

`runBenchClient` 启动 integrated client，由 Runtime 创建或复用配置的世界，应用窗口/渲染基线，执行 client Provider 并退出。`runBenchPaired` 则协调 dedicated server 与 1–8 个独立 physical client。两种模式不能混为一谈。

`runBenchClient` launches an integrated client. Runtime provisions or reuses the configured world, applies the window/render baseline, executes client Providers and exits. `runBenchPaired` coordinates a dedicated server and 1–8 separate physical clients. They are different execution modes.

## 世界与环境 / World and environment

```kotlin
modBench {
    seed = 7
    clientWorldPreset = "void"       // normal | flat | void
    clientDimension = "overworld"    // overworld | the_nether | the_end
    clientWindowWidth = 1280
    clientWindowHeight = 720
    clientRenderDistance = 4
    clientSimulationDistance = 4
    clientVsync = false
    clientRequireWindowFocus = true
}
```

`void` 使用原版 The Void 超平坦供给，保留出生平台；它减少地形负载，并不意味着游戏没有其他开销。专服使用 `serverLevelType`、`serverGeneratorSettings` 和同一个 `seed` 写入 `server.properties`。供给指纹变更可能重置 bench 专用世界，因此不要指向需要保留的个人存档。

The void preset uses vanilla The Void superflat provisioning with a spawn platform. It reduces terrain load, not all game overhead. Dedicated servers use `serverLevelType`, `serverGeneratorSettings` and the same seed in `server.properties`. Provisioning changes can reset benchmark-owned worlds; do not use personal worlds you need to preserve.

`context.environment().readiness()` 跟踪资源加载、屏幕、区块和 mesh 队列。失焦（启用焦点要求时）、最小化、暂停、非预期屏幕或尺寸变化会留下 invalidation；无效环境会把原本通过的整轮结果降为 `INCONCLUSIVE`，已有 `FAILED` / `ABORTED` 保留。不要通过关闭门禁掩盖不稳定测量。后台运行可显式关闭焦点要求，仍需保存参数并避免最小化。

Readiness tracks resources, screen state, chunks and mesh queues. Focus loss when required, minimization, pause, unexpected screens or resize can invalidate an otherwise passing run, producing `INCONCLUSIVE` while preserving existing `FAILED` / `ABORTED` outcomes. Do not disable gates to hide an unstable measurement. Background runs may explicitly relax focus requirements, but must record that choice and avoid minimization.

## 相机与截图 / Camera and screenshots

- 固定机位：`setPose`、`movePose`、`lookAt`、`stopMovement`
- 自动构图：`BenchBounds3` + `BenchCameraFraming` → `frameTarget` / `holdFramedTarget`
- 时间轴：`BenchCameraPath` 的 `to` / `toAtSpeed`、`hold`、`capture`、`mode`；每个 client tick 调 `BenchCameraPlayback.advance()`
- 截图：`captureScreenshot` 返回 future，跨 tick 轮询；保持默认 readiness/稳定帧门禁，按需隐藏 HUD
- 清理：teardown 中释放 `BenchPoseHold` 等场景拥有的资源；Runtime 在场景边界兜底

Use poses for fixed views, bounds/framing for target-aware composition, camera paths for keyframes, and screenshot futures for nonblocking capture. Advance playback once per client tick. Preserve capture gates and release owned holds during teardown.

构图使用真实窗口宽高比、FOV 和玩家眼高；`frameFill` 常用 0.75–0.85。构图只保证给定 bounds 的视锥比例，不证明目标未被遮挡。动态截图应由业务 probe 确认目标事件发生后请求，不能等一个任意的晚期 tick。原始 framebuffer PNG 是证据，事后裁剪不能恢复画外或只占几个像素的细节。

Framing uses the actual aspect ratio, FOV and eye height; a typical `frameFill` is 0.75–0.85. It does not prove that a target is unoccluded. Trigger dynamic captures from business-state probes, not an arbitrary late tick. Keep original framebuffer PNGs; cropping cannot restore missing or subpixel detail.

## GUI 自动化 / GUI automation

`automation().beginGuiSession(ExpectedScreen.class)` 把目标 Screen 声明为预期环境。用 `name(widget, "stable-id")` 注册稳定语义名，再通过 `BenchGuiSelector.semanticName("stable-id")` 严格选择。

Declare the expected screen with `beginGuiSession`, assign stable semantic names, and select widgets strictly. Zero matches wait or fail; ambiguous matches never silently select the first widget.

支持 `snapshot`、`await` / `awaitMissing`、`click` / `doubleClick`、`scroll`、`drag`、`pressKey`、`typeText` 和 `captureWidget`。输入沿 Minecraft/NeoForge Screen 链分发，控件截图在 render post 阶段重新解析 selector 并按 GUI scale 裁剪。只能在 client thread 调用；future 不得阻塞等待。

Snapshots, waits, clicks, scrolling, dragging, keys, Unicode input and widget-region captures are supported. Inputs follow the game screen dispatch path. Widget captures re-resolve the selector after rendering and account for GUI scale. Invoke these APIs only on the client thread and poll futures without blocking.

这是 interaction tree，不是完整视觉树：纯 `Renderable` 背景、直接绘制文字和 tooltip 等不自动包含。完整 accessibility/visual tree、tooltip 捕获和感知级截图比较仍不能视为已实现。

The snapshot is an interaction tree, not a complete visual tree. Render-only backgrounds, directly drawn text and tooltips are not automatically represented. Full accessibility/visual trees, tooltip capture and perceptual screenshot comparison are not established capabilities.

**26.3 输入迁移 / Input migration:** 26.3 使用 SDL 常量，主鼠标键为 **1**，不是旧 GLFW 的 0；默认点击/拖动已由 Runtime 适配，显式输入常量的调用方仍须迁移。`pressKey(key, keycode, modifiers)` 的第一个参数是 SDL scancode，第二个是布局 keycode。不要从旧版本照搬数字常量。

Minecraft 26.3 uses SDL constants: the primary mouse button is **1**, not GLFW 0. Default actions are adapted by Runtime, but callers supplying explicit constants must migrate. `pressKey(key, keycode, modifiers)` takes an SDL scancode first and a layout keycode second. Do not copy numeric input constants from an older version.

## Paired 模式 / Paired mode

```kotlin
modBench {
    pairedClientCount = 2
    pairedStartupTimeoutSeconds = 180
    pairedClientTimeoutSeconds = 900
    pairedServerScenarios = "yourmod.server-*"
    pairedClientScenarios = "yourmod.client-*"
    // Only when consuming a locally published development branch:
    pairedProjectProperties.put("modBenchLocal", "true")
    pairedProjectProperties.put("modBenchBinaryArtifacts", "true")
}
```

```sh
./gradlew runBenchPaired
```

协调器分配 loopback 端口，隔离用户名/目录，等待各端报告并回收进程树。结果在 `build/modBench/paired/default/summary.json`。父 Gradle 的任意 `-P` 参数不会自动继承，必须显式放进 `pairedProjectProperties`，且只放非敏感参数。

The coordinator allocates a loopback port, isolates names/directories, waits for participant reports and cleans up process trees. Its summary is `build/modBench/paired/default/summary.json`. Arbitrary parent Gradle `-P` flags are not inherited; explicitly forward necessary non-secret properties.

当前是 passthrough 纵切，phase barrier、nonce handshake、network profile 接线仍未完成。TCP stream 延迟/限速/断连、应用消息丢弃和真正 IP packet loss 属于不同语义；随机丢弃 TCP bytes 只会损坏协议。不要把网络模块存在解释成已获得可复现丢包测试。

The current paired path is a passthrough slice; phase barriers, nonce handshakes and network-profile wiring are unfinished. TCP stream delay/rate limits/disconnection, application-message dropping and real IP packet loss have distinct semantics. Dropping arbitrary TCP bytes corrupts the protocol. Network modules alone are not proof of a reproducible packet-loss experiment.

## 1.21.1 注意事项 / 1.21.1 notes

本分支使用旧版 OpenGL、输入与世界 API；修饰键通过客户端作用域适配保留，直接轮询 GLFW 的第三方控件不受影响。无显示服务时不能验证实际 GUI/截图或 mixin 应用。The legacy OpenGL/input/world adapters preserve scoped Screen modifier queries; custom raw GLFW polling stays physical. See [verified limits](../minecraft-1.21.1-port.md).
