# 编写场景 / Writing scenarios

[返回首页 / Home](Home.md)

## 文件布局 / File layout

```text
src/main/                         production mod
src/bench/java/                   test-only Provider and scenarios
src/bench/resources/META-INF/services/
  com.zhongbai233.bench.api.BenchProvider
```

ServiceLoader 文件每行是一个 Provider 全限定类名，例如 `com.example.bench.ExampleBenchProvider`。Provider 必须有公开无参构造器；构造器保持轻量，不创建世界、线程或 GPU 资源。

The ServiceLoader file contains one fully qualified Provider class name per line, for example `com.example.bench.ExampleBenchProvider`. Provide a public no-argument constructor and keep it lightweight. Do not create worlds, threads or GPU resources there.

## 最小服务器场景 / Minimal server scenario

以下文件是可编译的契约示例；它测量当前世界中已加载的实体数，用于证明 Provider、tick 和指标链路。它不是你的 Mod 的性能基准，接入后应替换为真实业务负载。

This complete contract example samples loaded entities to exercise discovery, ticks and metrics. It is not a benchmark of your mod's business workload; replace it with a real workload after integration.

```java
package com.example.bench;

import com.zhongbai233.bench.api.BenchApiVersion;
import com.zhongbai233.bench.api.BenchCompatibility;
import com.zhongbai233.bench.api.BenchMetricDescriptor;
import com.zhongbai233.bench.api.MetricDirection;
import com.zhongbai233.bench.api.ScenarioDescriptor;
import com.zhongbai233.bench.api.neoforge.server.*;
import java.time.Duration;
import java.util.Set;

public final class ExampleBenchProvider implements BenchServerProvider {
    public ExampleBenchProvider() {}
    @Override public String id() { return "example"; }
    @Override public BenchCompatibility compatibility() {
        return BenchApiVersion.currentCompatibility();
    }
    @Override public void registerServer(BenchServerRegistrar registrar) {
        registrar.register(new ScenarioDescriptor(
                "example.loaded-entities", "Loaded entity sample",
                Set.of("server"), Duration.ofSeconds(30)),
                context -> new EntityScenario());
    }
    private static final class EntityScenario implements BenchServerScenario {
        private static final BenchMetricDescriptor ENTITIES =
                new BenchMetricDescriptor("example.loaded_entities", "count",
                        MetricDirection.NEUTRAL);
        private int ticks;
        @Override public BenchStepResult measure(BenchServerContext context) {
            int count = 0;
            for (var ignored : context.level().getAllEntities()) count++;
            context.metrics().record(ENTITIES, count);
            return ++ticks >= 40 ? BenchStepResult.COMPLETE : BenchStepResult.CONTINUE;
        }
        @Override public void verify(BenchServerContext context) {
            if (ticks != 40) throw new AssertionError("Incomplete measurement");
        }
    }
}
```

## 生命周期 / Lifecycle

`setup → stabilize → warmup → measure → verify → teardown`

- `setup`：用目标 Mod 的公开 API 创建确定性负载，记住本场景拥有的对象
- `stabilize`：等待区块、业务状态或渲染准备好
- `warmup`：预热业务路径，避免把初始化混入测量
- `measure`：执行负载并记录指标
- `verify`：断言业务正确性；失败时抛出异常或 `AssertionError`
- `teardown`：精确释放本场景拥有的对象，容忍部分 setup；不要清空别人的世界内容

Setup creates deterministic state; stabilize waits for readiness; warmup exercises the hot path; measure records workload data; verify asserts correctness; teardown releases only scenario-owned state and tolerates partial setup.

`stabilize`、`warmup`、`measure` 每 tick 调一次，快速返回 `CONTINUE` 或 `COMPLETE`。不要 `sleep`、循环等待后续 tick，或在游戏线程上 `join()` future。客户端使用 `BenchClientScenario` / `BenchClientStepResult`，通过后续 client tick 非阻塞检查异步结果。超时由 Runtime 负责；全局 tick 预算与 `ScenarioDescriptor.phaseTimeout` 一起决定各阶段预算。

The three step phases run once per tick and must return promptly with `CONTINUE` or `COMPLETE`. Never sleep, spin waiting for future ticks, or join a future on the game thread. Client scenarios use `BenchClientScenario` and `BenchClientStepResult`, polling asynchronous results on later ticks. Runtime owns timeouts; both global tick limits and the descriptor's phase timeout contribute to the budget.

## 选择与验收 / Selection and acceptance

```sh
./gradlew runBenchServer -PmodBench.scenarios=example.loaded-entities
./gradlew runBenchClient '-PmodBench.scenarios=example.client-*'
```

过滤器支持逗号分隔 ID 和末尾 `*` 前缀匹配；受限过滤器没有匹配任何场景时会失败。混合有效 ID 与误拼 ID 并不保证报错。给场景和指标使用稳定、全局唯一 ID。通过正式报告验收，而不是只看进程退出码：

Filters accept comma-separated IDs and trailing-`*` prefix matching. A restricted filter that matches no scenario fails; a mix of valid and mistyped IDs may still succeed. Keep stable, globally unique scenario and metric IDs. Verify the report, not just the process exit code:

```kotlin
tasks.named<com.zhongbai233.bench.gradle.VerifyBenchReportTask>("verifyBenchServerReport") {
    expectedScenarioIds.set(listOf("example.loaded-entities"))
    expectedMetricNames.set(listOf("server.tick.duration", "example.loaded_entities"))
    expectedLoadedModIds.set(listOf("minecraft", "neoforge", "yourmodid", "modbench_runtime"))
}
```

使用 `context.artifacts().write("trace.csv", "text/csv", content)` 登记业务诊断文件。Runtime 保存路径、SHA-256 和字节数；不要另写一个声称成功但与 `summary.json` 矛盾的结果系统。

Register custom diagnostic files through `context.artifacts().write("trace.csv", "text/csv", content)`. Runtime records their paths, hashes and sizes. Do not create a parallel pass/fail reporting system that contradicts `summary.json`.
