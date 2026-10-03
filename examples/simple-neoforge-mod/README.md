> 26.2 工作分支：`0.1.3-beta-mc26.2` 尚未发布 JitPack tag。当前必须先发布本地源码，再显式使用 `-PmodBenchLocal=true`；本分支 CI 验证源码发布后的独立消费。下述远程消费方式适用于对应 tag 正式发布之后。

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
