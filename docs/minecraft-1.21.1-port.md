# Minecraft 1.21.1 port / Minecraft 1.21.1 回移

[中文](#中文) · [English](#english)

## 中文

### 目标与边界

`1.21.1` 是独立版本分支，以 `main` 的 BenchMod 核心基准测试工具链为基础。它保留 Gradle 插件、隔离的 `src/bench`、Provider 生命周期、服务端/客户端 API、相机路径、GUI 检查与输入、PNG 截图、JSON/JSONL/JFR 产物、报告验证和配对运行编排。

- Minecraft **1.21.1**、NeoForge **21.1.252**、Java **21**
- Gradle **9.5.1**、ModDevGradle **2.0.148**
- 开发版本 **0.1.3-mc1.21.1-beta**；尚未创建发布 tag
- API 模块：`bench-api-neoforge-1.21.1`
- Runtime 模块：`bench-runtime-neoforge-1.21.1`
- Java package 和 Gradle 插件 ID 保持不变；消费方必须使用本分支的成套产物

这是 OpenGL 游戏版本。`26.2` / `26.3` 的 Vulkan 选择器、现代 GPU 迁移探针及跨后端比较套件不在本分支中；不能把旧版截图或编译通过宣称为 Vulkan 验证。

### 实际适配

- 普通 ModDev runs 保持原有 main/额外 source set；独立的同名 bench ModModel 只供 bench runs 使用，防止 Provider 进入普通游戏运行
- 恢复 FML 4 所需的 `javafml` 元数据，Core 与 Minecraft 类型 API JAR 均标记为 `GAMELIBRARY`（Core 仍无平台依赖），确保 Provider/Runtime 与游戏类型处于兼容的类加载层
- 使用 1.21.1 的 `LevelSettings`、独立 `GameRules`、`RegistryAccess`、维度键和传送接口，保留 normal/flat/void 世界及指定维度
- 使用旧版 `Input`、实体位置/旋转接口、Screen/NeoForge 的坐标型输入回调；文本沿原版 `KeyboardHandler` 的 UTF-16 分发行为处理
- 截图在渲染线程读取真实 OpenGL framebuffer，PNG 编码仍在 IO 线程；裁剪使用此版本的 RGBA 像素 API
- 精确 access transformer 开放旧版私有 `GameRenderer.getFov(Camera,float,boolean)`，相机取景使用包含 NeoForge FOV hook 的真实投影，避免用固定角度估算
- 通过 GLFW 获取最小化状态；1.21.1 没有新版 AFK FPS 选项，因此诊断记录 `inactivity_fps_limit=not_available`

### 已知旧版行为

1. 旧版控件会读取 `Screen` 的 Shift/Ctrl/Alt 查询。Runtime 的客户端专用 mixin 在合成回调期间提供作用域隔离的修饰键状态，嵌套及异常退出均恢复原状态；直接读取 GLFW 原始物理键状态的第三方控件不受此适配影响
2. `doubleClick` 发送两次点击，由 1.21.1 控件自身判断双击；没有新版显式 double-click 标记
3. GUI 输入、截图、相机与配对客户端需要可用显示服务和 OpenGL 上下文；无图形环境下的测试不能证明这些运行路径的视觉正确性或性能
4. 帧间隔仍是 CPU 观察到的 frame interval，并非 GPU frame time

### 本地验证

安装 JDK 21，在仓库根目录执行：

```bash
./gradlew -PmodBenchBinaryArtifacts=true check verifyReleaseReadiness
./gradlew -p examples/simple-neoforge-mod \
  -PmodBenchLocal=true -PmodBenchBinaryArtifacts=true \
  compileBenchJava check sourcesJar
./gradlew -p examples/simple-neoforge-mod \
  -PmodBenchLocal=true -PmodBenchBinaryArtifacts=true \
  runBenchServer verifyBenchServerReport --configuration-cache
```

重复最后一条命令验证配置缓存复用和独立服务端可重复运行。有图形环境时再运行 `verifyBenchClient`；不要将 client 验证归入 server 测试结果。

`modBenchBinaryArtifacts=true` 选择 ModDev 的官方补丁二进制生成路径，省去 Minecraft 源码重新编译；BenchMod 自身源码、测试、真实游戏运行和产物验证仍正常执行。不带该属性时保留标准源码开发路径。

### 验证记录

2026-10-03：

- 根 `check verifyReleaseReadiness` 通过：**164 个 JUnit，0 失败、0 错误、0 跳过**；包含 GUI 修饰键作用域和普通运行 source-set 隔离测试
- 独立示例 `compileBenchJava check sourcesJar` 通过；普通 `runtimeClasspath`、生产 JAR 和 sources JAR 未包含 BenchMod/Provider 测试内容
- 真实 NeoForge 1.21.1 专用服务器运行两次，`simplebench.server-smoke` 均 `PASSED`，全部场景阶段完成并自动退出；JSON schema、必需 artifact、自定义指标及加载 mod 校验通过
- 第二次专服运行复用了 Gradle configuration cache；根 aggregate 检查也已验证缓存复用
- Core 与平台 API 的 `GAMELIBRARY` 标记、Java 21 bytecode 和发布依赖检查通过
- 实际客户端启动在 NeoForge early display 处失败：`glfwInit failed` / `cannot open display`。环境没有 DISPLAY/Xvfb；因此客户端 mixin 的实际应用、世界打开、GUI、截图、相机与配对客户端 **尚未在游戏中验收**。无 GPU 性能或视觉通过结论
- FML 4 的 JUnit launch 固定为 server side；修饰键测试验证作用域/异常/嵌套及配置和方法契约，不能代替客户端 transformation 或 GUI E2E

结论：**服务端已验收 beta；客户端实际运行待有图形环境时验证**。GitHub CI 对本分支执行精确提交的构建与独立专服验证；其状态以对应 Actions run 为准。

## English

### Scope and compatibility

The independent `1.21.1` branch backports the core BenchMod toolchain from `main`: isolated benchmark sources, provider lifecycle, server/client APIs, camera paths, GUI inspection/input, screenshots, JSON/JSONL/JFR artifacts, report verification and paired-process orchestration.

It targets Minecraft **1.21.1**, NeoForge **21.1.252**, JDK **21**, Gradle **9.5.1** and ModDevGradle **2.0.148**. The development version is **0.1.3-mc1.21.1-beta**; no release tag has been created. Use matching `bench-api-neoforge-1.21.1` and `bench-runtime-neoforge-1.21.1` artifacts from this branch. Java packages and the Gradle plugin ID remain unchanged.

This game version uses OpenGL. The Vulkan selector and modern graphics-migration probes from the `26.2` / `26.3` branches are not included. Compilation and legacy screenshots are not evidence of Vulkan validation.

### Platform changes and limitations

- A detached, identically named ModModel keeps benchmark sources out of ordinary runs while retaining extra target source sets
- FML 4 metadata and `GAMELIBRARY` manifests on both core and platform API (core stays dependency-free) keep Minecraft-bound signatures in the game classloader
- World creation uses the legacy `LevelSettings`, independent `GameRules`, registry and teleport APIs
- Mouse input uses coordinate callbacks. A client-only mixin scopes `Screen` Shift/Ctrl/Alt queries during synthetic input, with nested and exception-safe restoration; direct raw GLFW polling by custom widgets still observes physical input
- Double-click dispatch uses two normal clicks and the widget's own timing detection; text follows vanilla UTF-16 callbacks
- Real framebuffer readback happens on the render thread, with PNG encoding on the IO executor
- A narrowly scoped access transformer exposes `GameRenderer.getFov(Camera,float,boolean)` so camera framing uses the actual engine FOV, including NeoForge hooks
- Minimized-window detection uses GLFW; the newer inactivity-FPS option does not exist and is reported as `not_available`
- Client graphics and paired-client execution require a working display/OpenGL context. Headless server tests cannot prove client visual correctness or GPU performance. Frame interval is not GPU frame time

### Verification

Run the commands above with JDK 21. `verifyReleaseReadiness` checks and publishes the five public modules to Maven Local, enabling the independent example's `modBenchLocal=true` mode. The official patched-binary flag avoids recompiling Minecraft itself while still compiling and testing BenchMod against real game classes. Repeat the server command to check configuration-cache reuse. Run `verifyBenchClient` separately on a graphical machine.

Verified on 2026-10-03: all **164 JUnit tests** passed with no failures/errors/skips; publication preflight and independent consumer compilation/production-source-JAR isolation passed. The actual dedicated-server scenario passed twice, including schema, artifact, custom-metric and loaded-mod validation, automatic shutdown and repeated-run configuration-cache reuse. Java 21 bytecode, the core/platform API manifests and publication dependencies were inspected.

The real client launch failed in NeoForge early display at `glfwInit failed` / `cannot open display`. This environment has no DISPLAY/Xvfb. Actual client mixin application, world opening, GUI, screenshots, camera and paired-client execution remain **unverified in-game**; no GPU performance or visual pass is claimed. FML 4 JUnit runs server-side, so scope/config/method-contract tests are not client transformation tests.

Status: **server-validated beta; graphical client validation remains outstanding**. Exact-commit GitHub Actions runs are the authority for remote build/server CI status.
