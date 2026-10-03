# 版本与架构 / Versions and architecture

[返回首页 / Home](Home.md)

## 选择版本 / Choose a version

| Git branch | Minecraft | NeoForge | ModDev | Java | Source version | Adapter suffix |
| --- | --- | --- | --- | --- | --- | --- |
| `main` | 26.1.2 | 26.1.2.76 | 2.0.141 | 25 | 0.1.3-beta | `26.1` |
| `26.2` | 26.2 | 26.2.0.88 | 2.0.141 | 25 | 0.1.3-beta-mc26.2 | `26.2` |
| `26.3` | 26.3 | 26.3.0.45-beta | 2.0.148 | 25 | 0.1.3-beta-mc26.3 | `26.3` |
| 1.21.1 backport | 1.21.1 | 21.1.252 | 2.0.148 | 21 | 0.1.3-mc1.21.1-beta (in progress) | `1.21.1` (pending validation) |

三条 26.x 分支使用仓库 Wrapper（Gradle 9.5.1）。CI 同时安装 Java 21 和 25：项目工具链用 25，NeoForge 工具任务可能还需要 21。不要只把 Minecraft 版本字符串改掉，就复用另一条线的 API/Runtime。

The 26.x branches use Gradle Wrapper 9.5.1. CI installs both Java 21 and 25: the project toolchain is 25, while NeoForge tooling may also need 21. Changing a Minecraft version string does not port an adapter.

**26.3 的特殊之处 / Important for 26.3:** 该分支同时保留 26.1 和 26.3 模块；根 `minecraftVersion` 仍描述 26.1 基线，26.3 模块读取 `minecraft263Version` / `neoForge263Version`。插件从 ModDev 推断开发线，只支持该分支已有的 `26.1`、`26.3`，不包含 26.2。26.3 独立示例是 `examples/simple-neoforge-mod-26.3`；原 `simple-neoforge-mod` 继续验证 26.1 已发布消费形态。

The 26.3 branch retains both 26.1 and 26.3 modules. Root `minecraftVersion` still describes the 26.1 baseline; the new modules read `minecraft263Version` and `neoForge263Version`. The plugin infers the line from ModDev and supports only 26.1 and 26.3 on that branch. Use `examples/simple-neoforge-mod-26.3` for the new adapter. The original example continues to exercise the released 26.1 baseline.

`modBenchVersion` 是源码/本地发布版本；消费方的 `modbench_version` 是依赖版本；`bench-api-neoforge-26.1` 的后缀是开发线。它们不是同一个概念。26.2/26.3 文档采用本地发布流程，不能假定带 `mc26.x` 后缀的 tag 已在 JitPack 发布。

`modBenchVersion` identifies the source/local publication, `modbench_version` pins a consumer dependency, and a module suffix identifies an adapter line. These are distinct. The 26.2/26.3 instructions use local publication and do not assert that a matching JitPack tag exists.

## 模块边界 / Module boundaries

| Layer | Responsibility | Must not own |
| --- | --- | --- |
| `bench-api-core` | Platform-neutral Provider SPI, compatibility, descriptors, metrics and shared data | Minecraft, NeoForge, Gradle or JSON implementation dependencies |
| `bench-api-neoforge-<line>` | Version-specific server/client contracts and automation interfaces | Concrete benchmark execution |
| `bench-runtime-neoforge-<line>` | Lifecycle, tick scheduling, timeouts, sampling, automation and report writing | Consumer business workload definitions |
| `bench-gradle-plugin` | `bench` source set, ModDev runs, dependency isolation, verification and artifact collection | Replacing ModDev's private launch machinery |
| `bench-report-schema` | Versioned authoritative JSON contract and fixtures | A separate runtime |
| Consumer `src/bench` | Real workload setup, probes, assertions and precise cleanup | Threads, global execution or parallel reporting systems |
| `bench-network-*` | Network-profile contracts and proxy implementation work | A claim that paired impairment is fully wired or publicly released |

依赖方向是 `src/bench → src/main`。生产代码不依赖 bench，Runtime 只进入 bench run 的 mod classpath，Provider 通过游戏 classloader 中的 `ServiceLoader<BenchProvider>` 发现。`check` 会验证生产 JAR（以及存在时的 sources JAR）没有 bench 内容；仍应检查正常 `runtimeClasspath`。

The dependency direction is `src/bench → src/main`. Production code must not depend on bench code. The Runtime is loaded only for bench runs, and Providers are discovered through `ServiceLoader<BenchProvider>` in the game classloader. `check` validates production JAR isolation, including a sources JAR when present; inspect the ordinary runtime classpath too.

26.2/26.3 图形 API 门面只暴露注册入口，实际 GPU 代码保留在 Runtime mod 中。版本 API 的 FML `GAMELIBRARY` 标记使含 Minecraft 类型的接口在正确 classloader 中解析。不要把 Runtime 实现复制进普通 Java 库来绕过加载错误。

The graphics façade on 26.2/26.3 exposes registration while actual GPU code stays in the Runtime mod. Version-specific API JARs use FML `GAMELIBRARY` metadata so Minecraft-typed signatures resolve in the correct classloader. Do not move Runtime implementation into an ordinary Java library to work around loading errors.
