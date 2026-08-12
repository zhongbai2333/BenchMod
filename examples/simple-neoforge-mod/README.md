# Simple NeoForge Mod consumer

这个独立示例验证外部 NeoForge Mod 如何消费已发布的 ModBench。默认从 JitPack 解析
`gradle.properties` 中固定的版本，不依赖本机 Maven Local：

```bash
./gradlew -p examples/simple-neoforge-mod check
```

开发 BenchMod 本身时，先把当前源码发布到 Maven Local，再显式切换示例的仓库和坐标：

```bash
./gradlew publishToMavenLocal
./gradlew -p examples/simple-neoforge-mod check -PmodBenchLocal=true
```

不要把 `modBenchLocal` 写入 `gradle.properties`。默认路径必须始终代表全新外部消费方，CI 也会在
隔离的 Gradle 用户目录中验证 JitPack 插件、API 和 Runtime 依赖能够解析。
