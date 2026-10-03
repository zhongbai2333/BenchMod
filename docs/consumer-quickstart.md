# Minecraft 1.21.1 consumer quickstart / 消费方快速接入

此分支尚未发布 JitPack tag。`0.1.3-beta` 是其他版本线的产物，不能用于本分支的 1.21.1 API/Runtime。请先从本分支源码发布到 Maven Local。

This branch has no published JitPack tag. The older `0.1.3-beta` artifacts target another game line. Publish this branch locally before using its matching 1.21.1 modules.

## 已验证路径 / Verified workflow

需要 JDK 21。仓库根目录执行 / From the repository root with JDK 21:

```sh
./gradlew -PmodBenchBinaryArtifacts=true check verifyReleaseReadiness
./gradlew -p examples/simple-neoforge-mod -PmodBenchLocal=true -PmodBenchBinaryArtifacts=true compileBenchJava check
./gradlew -p examples/simple-neoforge-mod -PmodBenchLocal=true -PmodBenchBinaryArtifacts=true verifyBenchServer
```

独立示例设置了 Provider 数量、场景 ID、必需 artifact、指标及 mod ID，并验证生产 JAR/sources JAR。重复最后一条命令验证配置缓存。图形客户端需单独执行 `verifyBenchClient`，本云端环境没有可用显示服务。

The example validates provider count, scenario ID, artifacts, metrics, loaded mods and production/source-JAR isolation. Repeat the last command for configuration-cache reuse. `verifyBenchClient` requires a separate graphical environment.

## 自己的 Mod / Your mod

- NeoForge 21.1.252、ModDevGradle 2.0.148、JDK 21
- Plugin ID: `com.zhongbai233.minecraft-bench`
- Local group/version: `com.zhongbai233.bench` / `0.1.3-mc1.21.1-beta`
- API: `bench-api-core` + `bench-api-neoforge-1.21.1`
- Runtime: `bench-runtime-neoforge-1.21.1`, only on the benchmark runtime
- Provider: `src/bench/java`; service descriptor: `src/bench/resources/META-INF/services/com.zhongbai233.bench.api.BenchProvider`

复制 [独立示例的 settings/build](../examples/simple-neoforge-mod) 并替换 target mod 与报告期望；不要把 BenchMod Runtime 加入生产 `runtimeOnly`。普通 runs 保持原有 source set，独立 bench ModModel 才附加 Provider。

Copy the [independent example settings/build](../examples/simple-neoforge-mod), then change the target mod and report expectations. Do not add the runtime to production `runtimeOnly`; only the detached benchmark ModModel includes providers.

继续阅读 / Read more: [场景编写](wiki/Writing-Scenarios.md), [客户端自动化](wiki/Client-Automation.md), [1.21.1 适配与验证边界](minecraft-1.21.1-port.md).
