> 26.2 的发布版为 `0.1.6-beta-mc26.2`。常规使用通过 JitPack 远程获取依赖；只有在验证 BenchMod 本地源码时，才先将 BenchMod 发布到 Maven Local，并在构建本示例时显式传入 `-PmodBenchLocal=true`。

# Simple NeoForge Mod consumer

这个独立示例验证外部 NeoForge Mod 如何消费匹配版本的 ModBench。未启用本地模式时从 JitPack 解析
`gradle.properties` 中固定的版本，不依赖本机 Maven Local：

```bash
./gradlew -p examples/simple-neoforge-mod check
```

开发 BenchMod 本身时，先把当前源码发布到 Maven Local，再显式切换示例的仓库和坐标：

```bash
./gradlew publishToMavenLocal
./gradlew -p examples/simple-neoforge-mod check -PmodBenchLocal=true
```

不要把 `modBenchLocal` 写入 `gradle.properties`。本分支 CI 使用独立 Gradle 用户目录和显式本地模式验证当前源码；远程发布验证应在对应 26.2 tag 发布后恢复。
