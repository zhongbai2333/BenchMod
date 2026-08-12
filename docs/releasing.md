# 通过 JitPack 发布 ModBench

ModBench 使用 JitPack 从不可变 Git tag 构建，不需要向 Maven Central 申请 namespace、配置签名或管理中央仓库凭据。

## 发布前检查

要求 Java 25，然后在干净工作树上用计划发布的 tag 做一次与 JitPack 坐标一致的检查：

```bash
JITPACK=true \
GROUP=com.github.zhongbai2333 \
ARTIFACT=BenchMod \
VERSION=0.1.2 \
./gradlew check verifyReleaseReadiness --configuration-cache --no-daemon --stacktrace
```

`verifyReleaseReadiness` 执行五个公开模块的 `check` 和 `publishToMavenLocal`，根 `check` 还覆盖暂未发布的 network 模块。GitHub CI 对分支构建使用 commit SHA、对 tag 构建使用 tag 名注入同一组 JitPack 环境变量，并由 Wrapper Validation 检查 Wrapper JAR、checksum 和脚本；因此正式 tag 的 POM 与插件内自动依赖坐标会在 JitPack 构建前得到等价验证。JitPack 也必须先成功执行已提交的 `./gradlew` 才会进入构建。公开模块是：

- `bench-api-core`
- `bench-report-schema`
- `bench-api-neoforge-26.1`
- `bench-runtime-neoforge-26.1`
- `bench-gradle-plugin`

`bench-network-*` 尚未成为稳定公共发布面，因此不会发布；它们的测试仍由上面的根 `check` 覆盖。

## 创建版本

根项目和示例项目的版本属性用途不同：

- 根目录 `gradle.properties` 的 `modBenchVersion` 表示当前源码和计划创建的 tag；
- `examples/simple-neoforge-mod/gradle.properties` 的 `modbench_version` 固定最后一个已经确认可用的 JitPack tag。

因此发布候选阶段不要求二者相等。示例必须继续验证旧的稳定版本，直到新 tag 的 JitPack 产物实际可用。

1. 把根目录 `gradle.properties` 中的 `modBenchVersion` 改成计划发布的版本，并确认目标 commit 的 CI 已通过。JitPack 的正式版本仍由 tag 注入，但仓库内版本必须与 tag 保持一致。
2. 创建并推送不可变 tag，例如：

   ```bash
   git tag -a 0.1.2 -m "ModBench 0.1.2"
   git push origin 0.1.2
   ```

3. 打开 [JitPack 的 BenchMod 页面](https://jitpack.io/#zhongbai2333/BenchMod)，选择该 tag 并触发或查看构建。
4. 等待五个模块均可解析，用独立消费方完成下方最小验证，再更新示例和其他消费方的 `modbench_version`。

不要移动或复用已经被消费的 tag。修复发布内容时创建新的 patch 版本。

JitPack 构建会通过 `jitpack.yml` 安装 NeoForge 工具任务需要的 Java 21 和 BenchMod 构建使用的 Java 25，并运行 `clean check verifyReleaseReadiness`。构建环境提供的坐标会写入 Gradle Plugin 自身，因此其自动依赖与同一次 JitPack 构建保持一致：

```text
com.github.zhongbai2333.BenchMod:bench-api-core:<tag>
com.github.zhongbai2333.BenchMod:bench-api-neoforge-26.1:<tag>
com.github.zhongbai2333.BenchMod:bench-runtime-neoforge-26.1:<tag>
```

## 发布后的最小验证

在一个不含 `mavenLocal()` 的消费方中应用插件，运行：

```bash
./gradlew tasks --all
./gradlew check
```

确认存在 `runBenchServer`、`runBenchClient`、`verifyBenchServer` 和 `verifyBenchClient`，且自动注入的 API/Runtime 坐标来自 JitPack。需要完整验收时再执行对应的 `verifyBench*`，它们会启动 Minecraft，不能放进普通快速 CI。

仓库内 `examples/simple-neoforge-mod` 默认就是该远程消费形态；CI 的
`Verify released JitPack consumer contract` job 使用独立 `GRADLE_USER_HOME`、刷新依赖并执行
`compileBenchJava` 与 `benchRuntimeClasspath` 解析，防止本机 Maven Local 或已有 Gradle 缓存掩盖发布问题。
该 job 验证的是示例中记录的“最后一个已确认版本”，而发布候选本身由
`verifyReleaseReadiness` 在 JitPack 环境坐标下验证。新 tag 验收完成后更新示例版本，后续 CI 才会持续监控它。
