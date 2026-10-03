# 图形移植助手 / Graphics migration assistant

[返回首页 / Home](Home.md) · [版本表 / Versions](Versions-and-Architecture.md)

## 范围 / Scope

本功能只在 `26.2` / `26.3` 分支，按需启用。它为 OpenGL → Blaze3D / Renderpearl 迁移提供真实 GPU 正确性探针；普通 BenchMod 消费方不会自动执行。`main` 26.1.2 和正在移植的 1.21.1 不应被描述为拥有这套高版本场景。

This opt-in suite is available on the `26.2` and `26.3` branches. It tests real GPU correctness for OpenGL → Blaze3D / Renderpearl migrations and does not run automatically for ordinary consumers. Do not assume it exists on main/26.1.2 or the in-progress 1.21.1 backport.

在 client Provider 的 `registerClient` 中调用：

Call this from your client Provider's `registerClient` method:

```java
GraphicsMigrationSuite.register(registrar);
```

该类位于匹配版本 API 的 `com.zhongbai233.bench.api.neoforge.graphics`。仅从 bench client 入口加载，不能在生产代码、专服初始化或普通静态初始化中访问。完整独立消费方是 `examples/graphics-migration-assistant`。

The entry point belongs to `com.zhongbai233.bench.api.neoforge.graphics` in the matching API. Load it only from a bench client Provider, never from production code or a dedicated-server/static initialization path. See the independent `examples/graphics-migration-assistant` consumer.

## 六个必需探针 / Six required probes

| Probe | Checks |
| --- | --- |
| `rgba-stride-orientation` | 17×9 asymmetric RGBA, source offset/padding/bottom-up rows; exact upload/readback bytes |
| `rg8-uv-channels` | Real RG8 upload and shader sampling; independent U/V channels, alpha and format semantics |
| `buffer-reuse` | Three completed write/copy/fence cycles on reused GPU buffers; detects stale/early reuse |
| `shader-depth-blend` | Shader/vertex/uniform bindings, explicit depth and translucent depth-write/blend behavior |
| `offscreen-copy` | Explicit color attachment, pass completion and copy into a separate texture |
| `resource-recreate` | Sixteen GPU-complete create/upload/readback/close cycles and suite-owned resource accounting |

每个 required probe 必须真正提交 GPU 工作并读回，再与独立 CPU oracle 逐通道比较；CPU fixture 不能冒充 GPU 执行。指定 probe 最多允许 2 个 8-bit 量化级的单通道误差，不自动翻转、裁剪或做颜色校正。

Every required probe submits GPU work and compares readback with an independent CPU oracle. CPU fixtures cannot substitute for GPU execution. Designated probes allow at most two 8-bit quantization levels of per-channel tolerance, with no automatic flip, crop or color correction.

`engine-resource-reload` 目前明确为可选 `SKIP`；该可选跳过不阻止六个 required probe 全部有效通过后的整体 PASS。关闭/重建测试只覆盖 suite 自有资源，不能证明 F3+T、引擎全局 shader cache 或驱动全局无泄漏。

`engine-resource-reload` is explicitly an optional `SKIP`; this does not prevent overall PASS when all six required probes supply valid passing evidence. Closing/recreating suite-owned resources does not validate F3+T, all engine shader caches or driver-global leak freedom.

## 构建与双后端实测 / Build and run both backends

在对应分支、Java 25 环境中，从仓库根执行：

From the repository root on the matching branch with Java 25:

```sh
./gradlew -PmodBenchBinaryArtifacts=true check verifyReleaseReadiness
./gradlew -p examples/graphics-migration-assistant -PmodBenchLocal=true -PmodBenchBinaryArtifacts=true compileBenchJava check
```

然后在**同一台有图形设备的机器**上运行。下例保存目录必须尚不存在；每轮会清理默认 client 结果，因此按顺序保存整个目录。Windows 用 `Copy-Item -Recurse` 替换 `cp -R`。

Then run on the **same graphics-capable machine**. The destination directories below must not already exist. Each run cleans the default client results, so preserve the whole directory before the next run. Use `Copy-Item -Recurse` instead of `cp -R` in PowerShell.

```sh
./gradlew -p examples/graphics-migration-assistant -PmodBenchLocal=true -PmodBenchBinaryArtifacts=true -PmodBench.client.graphicsBackend=opengl verifyBenchClient
cp -R examples/graphics-migration-assistant/build/modBench/raw-results/default/client saved-opengl
./gradlew -p examples/graphics-migration-assistant -PmodBenchLocal=true -PmodBenchBinaryArtifacts=true -PmodBench.client.graphicsBackend=vulkan verifyBenchClient
cp -R examples/graphics-migration-assistant/build/modBench/raw-results/default/client saved-vulkan
python3 tools/graphics_migration_compare.py saved-opengl/artifacts/custom/graphics-migration.json saved-vulkan/artifacts/custom/graphics-migration.json --output comparison.json --diff-dir comparison-diffs
```

比较器只用 Python 标准库。输入是登记为正式 artifact 的 `graphics-migration/1` sidecar 及其相邻原始 RGBA 文件，不是任意 `summary.json`。它验证相同 Minecraft/scene/revision/seed/尺寸/阈值、准确 backend、设备名称/vendor、环境有效性、文件长度/SHA-256/路径，然后才比较像素。driver 字符串可因 API 不同而不同；deviceName 必须严格一致。跨 Minecraft 版本比较被拒绝。

The standard-library-only comparator takes `graphics-migration/1` sidecars and their referenced raw RGBA files, not arbitrary summaries. It checks version, scene/revision/seed, dimensions/tolerances, actual backend, device/vendor, environment validity, lengths, hashes and paths before comparing pixels. API-specific driver strings may differ; device names must match exactly. Cross-Minecraft-version comparison is rejected.

每个读回场景保存 expected/actual RGBA、PNG 和 diff PNG；JSON 保存设备、oracle 与诊断。缺设备、backend 回退、required capability 不支持、超时或缺失场景属于 `BLOCKED`，不能修改字段伪装成功。比较器仅在全部 required scene 有完整 PASS 证据时返回 PASS：退出码 0=PASS，1=FAIL/INCONCLUSIVE，2=命令或输出错误。

Readback scenes preserve expected/actual RGBA, PNG and difference PNG files. Missing devices, fallback backends, unsupported required capabilities, timeouts or missing scenes are blocked evidence, not success. Never edit backend/status fields to pass validation. Exit codes are 0 for PASS, 1 for FAIL/INCONCLUSIVE, and 2 for invocation/output errors.

## 当前验证边界 / Current evidence boundary

已有 headless 检查覆盖真实版本 API 编译、fixture/oracle、状态机、无设备 BLOCKED、schema、比较器负例、发布隔离及独立消费方服务端回归。26.2/26.3 还包含真实 shader 前端编译检查。这些都不等同于显卡实测。

Headless validation covers actual API compilation, fixtures/oracles, state machines, no-device BLOCKED behavior, schemas, adversarial comparator inputs, publication isolation and independent server regression. Both branches also include real shader frontend compilation. None of these is a hardware GPU run.

本次高版本交付没有已验证的 OpenGL/Vulkan 图形运行证据，因此不宣称像素一致、渲染性能提升或硬件基准成功。必须取得上面的双后端真实报告后再做结论。更深入的版本说明：[26.2](https://github.com/zhongbai2333/BenchMod/blob/26.2/docs/graphics-migration-assistant.md) · [26.3](https://github.com/zhongbai2333/BenchMod/blob/26.3/docs/graphics-migration-assistant-26.3.md)。

The current high-version delivery has no verified OpenGL/Vulkan graphical-run evidence. It therefore makes no claim of pixel parity, performance improvement or successful hardware benchmarking. Obtain both real-device reports before drawing those conclusions.
