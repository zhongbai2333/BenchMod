# 外部 Mod 接入 ModBench

面向想给自己的 NeoForge Mod 加基准测试的作者。整个接入面是：应用一个插件、写一个 Provider，其余（运行、报告、验收、清理、收集）全部自动。

## 前置条件

- Mod 在 **Minecraft 26.1.2 / NeoForge 26.1.2.76** 开发线上（当前唯一验证线）。
- Java 25 工具链、Gradle 9.x、ModDevGradle 2.x。
- ModBench 尚未发布到远程仓库，先从本仓库发布到本地 Maven：

```bash
./gradlew publishToMavenLocal
```

> 修改过 ModBench 源码后要重新执行上面这条，消费方才能拿到新版本。

## 1. settings.gradle.kts

```kotlin
pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.neoforged.net/releases")
    }
    plugins {
        id("com.zhongbai233.minecraft-bench") version providers.gradleProperty("modbench_version").get()
    }
}

plugins {
    id("net.neoforged.moddev.repositories") version "2.0.141"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenLocal()
        mavenCentral()
        maven("https://maven.neoforged.net/releases")
        gradlePluginPortal()
    }
}
```

`gradle.properties` 里加：

```properties
modbench_version=0.1.0-SNAPSHOT
```

## 2. build.gradle.kts

```kotlin
plugins {
    java
    id("net.neoforged.moddev")
    id("com.zhongbai233.minecraft-bench")
}

modBench {
    targetMod = "yourmodid"        // 项目只有一个 mod 时可省略
    expectedProviderCount = 1
    seed = 7                       // 同时决定 dedicated server 的 level-seed
    phaseTimeoutTicks = 600
    // clientWorldPreset = "void"      // normal | flat | void（虚空：地形负载归零）
    // clientDimension = "the_end"     // overworld | the_nether | the_end
    // serverLevelType = "minecraft:flat"
    // jfrEnabled = true               // 每个实际执行场景录制低开销 JFR，产物 artifacts/jfr/<scenario-id>.jfr
}
```

不需要声明任何 ModBench 依赖——插件按自己的版本自动把 API 加进 `benchImplementation`、Runtime Mod 加进 `benchRuntimeMod`。想手动管理时设 `modBench.automaticDependencies = false`。

## 3. 写 Provider

代码放在 `src/bench/java`（可以依赖 `src/main`，反向禁止）：

```java
public final class MyBenchProvider implements BenchServerProvider {
    public MyBenchProvider() {}
    @Override public String id() { return "mymod"; }
    @Override public BenchCompatibility compatibility() { return BenchApiVersion.currentCompatibility(); }

    @Override
    public void registerServer(BenchServerRegistrar registrar) {
        registrar.register(
            new ScenarioDescriptor("mymod.server-workload", "Real workload", Set.of("server"), Duration.ofSeconds(30)),
            context -> new MyScenario());
    }
}
```

ServiceLoader descriptor 放在
`src/bench/resources/META-INF/services/com.zhongbai233.bench.api.BenchProvider`，内容是 Provider 全限定名。

场景在 server thread 用 Mod 的公开 API 搭真实 workload；`stabilize`/`warmup`/`measure` 每 tick 调一次，返回 `CONTINUE`/`COMPLETE`；自定义指标用 `context.metrics().record(descriptor, value)` 提交，会带完整分布统计进报告。客户端渲染场景另实现 `BenchClientProvider`，用 `context.automation()` 的相机路径与截图能力。

客户端截图应围绕“关键对象 bounds”构图，而不是围绕玩家出生点猜一个 pose：

1. 用 `BenchBounds3(minX, minY, minZ, maxX, maxY, maxZ)` 覆盖需要看清的 mesh、附件或实体；细长目标也至少给可见厚度留出 bounds。
2. 建立 `BenchCameraFraming(bounds, directionX, directionY, directionZ, frameFill)`；方向是从相机指向目标，`frameFill=0.75~0.85` 通常适合诊断截图。
3. 调用 `holdFramedTarget(...)`，让 Runtime 按实际窗口宽高比、FOV 和玩家眼高计算 pose，并在每个 client tick 保持机位。
4. 保存并轮询 `captureScreenshot(...)` 返回的 future；不要在 render/client thread 上阻塞等待。
5. teardown 中释放 `BenchPoseHold`。Runtime 会在场景边界兜底释放，但显式释放更清楚。

构图只能保证提供的 3D bounds 以足够比例进入视锥，不能证明目标未被地形遮挡，也不能替代业务状态断言。对“传输中”“碰撞瞬间”等动态内容，应由场景在业务 probe 表明动作发生时立即请求截图，不能等到固定的晚期 tick。不要把 PNG 后裁剪当成修复：画外、LOD、剔除或只剩几像素的信息无法靠裁剪找回来。

## 4. 运行与验收

```bash
./gradlew runBenchServer          # 真实 dedicated server 基准
./gradlew runBenchClient          # 真实 integrated client 基准
./gradlew check                   # 含生产 JAR / sources JAR 无 bench 内容检查
```

### 规划中的双端专服与网络模式

`runBenchClient` 仍是 integrated client。只在 separate client 连接 dedicated server 时出现的登录、同步、ping、卡顿和断连问题，将由独立 paired 模式覆盖；不要通过同时手工启动现有两个任务来近似。

paired network profile 会区分三层语义：

- `TCP_STREAM`：跨平台默认，可模拟上下行延迟/抖动、限速、转发暂停和断连；
- `APPLICATION_MESSAGE`：测试特定 Mod payload 的逻辑丢弃/乱序/重复；
- `IP_PACKET`：真实 packet loss/reorder/duplicate，需要平台原生且通常有权限要求的 backend。

Minecraft 使用 TCP，因此 stream proxy 丢弃 bytes 只会损坏协议，并不等价于真实丢包。ModBench 会在启动前校验 profile 与 backend capability，不支持的组合会明确 `INCOMPATIBLE`，不会静默降级。

只跑部分场景（逗号分隔，尾部 `*` 按前缀匹配；拼错会 FAILED 并列出可用 id）：

```bash
./gradlew runBenchClient -PmodBench.scenarios=mymod.rope-air-rest
```

场景内可用 `context.artifacts().write("trace.csv", "text/csv", content)` 把自有产物登记进报告（带 SHA-256，落在 `artifacts/custom/`）；`ScenarioDescriptor` 声明的 `phaseTimeout` 会自动抬高该场景的 phase 预算，长测量不必调大全局 `phaseTimeoutTicks`。

产物位置：

- 权威报告 `build/modBench/raw-results/default/<runType>/summary.json`（另有 `report.md` 派生视图）
- 原始样本 `artifacts/samples/<scenario>.jsonl`、截图、超时线程 dump
- 打包 bundle `build/modBench/bundles/default/<runType>/`（run 结束自动收集，失败也收）

验收自己的场景：

```kotlin
tasks.named<com.zhongbai233.bench.gradle.VerifyBenchReportTask>("verifyBenchServerReport") {
    expectedScenarioId.set("mymod.server-workload")
    expectedMetricNames.set(listOf("server.tick.duration", "mymod.your.metric"))
    expectedLoadedModIds.set(listOf("minecraft", "neoforge", "yourmodid", "modbench_runtime"))
}
```

完整验收标准见 [consumer checklist](../.github/skills/adapt-neoforge-mod/references/consumer-checklist.md)；可运行的完整示例见 [`examples/simple-neoforge-mod`](../examples/simple-neoforge-mod)。
