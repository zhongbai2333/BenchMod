# Minecraft 1.21.1 independent consumer / 独立消费示例

使用 JDK 21、NeoForge 21.1.252。此分支尚无已发布 JitPack tag；必须先在仓库根目录发布本分支到 Maven Local。

Use JDK 21 and NeoForge 21.1.252. No JitPack tag is published for this branch. From the repository root:

```sh
./gradlew -PmodBenchBinaryArtifacts=true check verifyReleaseReadiness
./gradlew -p examples/simple-neoforge-mod -PmodBenchLocal=true -PmodBenchBinaryArtifacts=true compileBenchJava check
./gradlew -p examples/simple-neoforge-mod -PmodBenchLocal=true -PmodBenchBinaryArtifacts=true verifyBenchServer
```

服务端报告 / Server report: `build/modBench/raw-results/default/server/summary.json` within this example.

JFR 按场景输出 / JFR is per scenario: `artifacts/jfr/simplebench.server-smoke.jfr`.

可用图形环境下执行 `verifyBenchClient`；无显示服务的 CI 不能证明截图/GUI/GPU 正确性。For client validation, run `verifyBenchClient` on a graphical machine. Headless CI cannot establish screenshot/GUI/GPU correctness.

See [1.21.1 verification notes](../../docs/minecraft-1.21.1-port.md) and the [consumer quickstart](../../docs/consumer-quickstart.md).
