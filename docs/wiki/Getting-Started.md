# 快速接入 / Getting started

[返回首页 / Home](Home.md) · [版本表 / Version matrix](Versions-and-Architecture.md)

## 1. 先运行独立示例 / Start with the independent example

安装所选分支需要的 JDK，克隆仓库并切换到对应分支。以下命令均从仓库根目录执行；Windows PowerShell 将 `./gradlew` 替换为 `.\gradlew.bat`。客户端运行需要显示器和可用驱动，服务器 smoke 不需要图形设备。

Install the required JDKs, clone the repository and select the matching branch. Run the following commands from the repository root. In Windows PowerShell replace `./gradlew` with `.\gradlew.bat`. A client needs a display and working graphics drivers; a dedicated-server smoke does not.

### main / Minecraft 26.1.2

```sh
git clone https://github.com/zhongbai2333/BenchMod.git
cd BenchMod
./gradlew check verifyReleaseReadiness
./gradlew -p examples/simple-neoforge-mod -PmodBenchLocal=true compileBenchJava check
./gradlew -p examples/simple-neoforge-mod -PmodBenchLocal=true verifyBenchServer
```

`verifyReleaseReadiness` 会执行检查并发布到 Maven Local，因此这套命令验证的是当前源码。要验证示例固定的 JitPack `0.1.3-beta`，省略 `-PmodBenchLocal=true`；不要混淆两种验证结果。

`verifyReleaseReadiness` checks and publishes to Maven Local, so these commands exercise the current source. Omit `-PmodBenchLocal=true` to exercise the example's pinned JitPack `0.1.3-beta` instead. Keep these two forms of evidence separate.

### 26.2

```sh
git switch 26.2
./gradlew -PmodBenchBinaryArtifacts=true check verifyReleaseReadiness
./gradlew -p examples/simple-neoforge-mod -PmodBenchLocal=true -PmodBenchBinaryArtifacts=true compileBenchJava check verifyBenchServer
```

### 26.3

```sh
git switch 26.3
./gradlew -PmodBenchBinaryArtifacts=true check verifyReleaseReadiness
./gradlew -p examples/simple-neoforge-mod-26.3 -PmodBenchBinaryArtifacts=true compileBenchJava check verifyBenchServer
```

26.3 专用示例已经显式使用 Maven Local。`modBenchBinaryArtifacts=true` 选择 ModDev 的 patched-binary 路径，减少源码重编译开销，不是模拟 Minecraft API。每次修改源码后重新发布；切换分支时不要误用另一条线的旧本地产物。

The dedicated 26.3 example explicitly uses Maven Local. `modBenchBinaryArtifacts=true` selects ModDev's patched-binary path; it does not substitute a mocked API. Republish after source changes and avoid consuming stale artifacts from another branch.

## 2. 接入自己的 Mod / Integrate your mod

先让普通 NeoForge Mod 正常构建，再增加 BenchMod。完整可运行配置以对应分支的独立示例为准。以下 JitPack 配置针对已发布的 26.1 基线；26.2/26.3 开发使用上述本地发布示例。

Get the normal NeoForge mod building first. Then add BenchMod, using the matching independent example as the complete configuration. The JitPack setup below targets the released 26.1 baseline; use the local-publication examples above for 26.2/26.3 development.

在 `settings.gradle.kts` 的 `pluginManagement` 仓库中加入 JitPack、Gradle Plugin Portal、Maven Central 与 NeoForge Maven，并映射插件 ID：

Add JitPack, the Gradle Plugin Portal, Maven Central and NeoForge Maven to `pluginManagement.repositories`, then map the plugin ID:

```kotlin
pluginManagement {
    repositories {
        maven("https://jitpack.io")
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.neoforged.net/releases")
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "com.zhongbai233.minecraft-bench") {
                useModule("com.github.zhongbai2333.BenchMod:bench-gradle-plugin:${requested.version}")
            }
        }
    }
}
```

同时在依赖仓库加入 JitPack；保留原 ModDev 仓库插件与配置。完整设置见 [26.1 接入配置](https://github.com/zhongbai2333/BenchMod/blob/main/docs/consumer-quickstart.md)。这是合并片段，不是替换整个已有 settings 文件。

Also add JitPack to dependency repositories and preserve the existing ModDev repository plugin/configuration. See the [complete 26.1 settings](https://github.com/zhongbai2333/BenchMod/blob/main/docs/consumer-quickstart.md). This is a merge fragment, not a replacement for an existing settings file.

在 `build.gradle.kts` 应用插件（保留已有 Java/ModDev 插件）：

Apply the plugin in `build.gradle.kts`, keeping the existing Java and ModDev plugins:

```kotlin
plugins {
    id("com.zhongbai233.minecraft-bench") version "0.1.3-beta"
}

modBench {
    targetMod = "yourmodid"
    expectedProviderCount = 1
    seed = 7
    phaseTimeoutTicks = 600
    jfrEnabled = true
}
```

插件默认将匹配的 API 注入 `benchImplementation`，Runtime 注入 `benchRuntimeMod`。`automaticDependencies = false` 仅用于显式管理完整匹配依赖的高级场景。不要把 Runtime 加到普通 `implementation` 或 `runtimeOnly`。

The plugin adds matching APIs to `benchImplementation` and the Runtime to `benchRuntimeMod`. Use `automaticDependencies = false` only when explicitly managing the complete matching dependency set. Never add the Runtime to ordinary `implementation` or `runtimeOnly`.

## 3. 加 Provider 并验收 / Add a Provider and verify

按[场景指南](Writing-Scenarios.md)编写 `src/bench/java` 和 ServiceLoader descriptor，然后在消费方根目录执行：

Follow [Writing scenarios](Writing-Scenarios.md) to add `src/bench/java` and the ServiceLoader descriptor, then run from the consumer root:

```sh
./gradlew compileBenchJava check
./gradlew verifyBenchServer
# After registering a client scenario, on a graphics-capable machine:
./gradlew verifyBenchClient
```

`check` 不会替你启动全部真实游戏场景。`verifyBenchServer` / `verifyBenchClient` 才组合实际运行与报告验收。验证完成后，在 `build/modBench/raw-results/default/<server|client>/summary.json` 查看权威报告。

`check` does not run every real game scenario. `verifyBenchServer` and `verifyBenchClient` combine a real run with report verification. Read the authoritative result at `build/modBench/raw-results/default/<server|client>/summary.json`.
