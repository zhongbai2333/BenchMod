# Graphics migration assistant

独立、test-only 的 OpenGL → Blaze3D 图形正确性场景消费示例。
完整场景、双后端运行、报告比较与能力边界见 [使用文档](../../docs/graphics-migration-assistant.md)。

从仓库根目录运行：

```sh
./gradlew -PmodBenchBinaryArtifacts=true publishToMavenLocal
./gradlew -p examples/graphics-migration-assistant -PmodBenchLocal=true -PmodBenchBinaryArtifacts=true compileBenchJava check
```

GPU 执行另需显示器/驱动。默认 seed=602263，scenario=`graphics-migration.suite`。
示例 `src/main` 只含空目标 Mod；Provider 位于 `src/bench`，生产 JAR 和普通 runtime classpath
由现有 ModBench 插件验证隔离。它没有专服场景；专服回归使用相邻 simple-neoforge-mod 示例。
