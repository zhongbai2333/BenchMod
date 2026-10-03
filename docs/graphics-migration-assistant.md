# Graphics migration assistant (26.2)

这是高版本分支的**独立、按需启用**图形移植场景集，适合把直接 OpenGL 调用迁到
Blaze3D（26.2）或 Renderpearl（26.3）时验证公共 GPU 抽象是否仍保持正确数据和渲染语义。
普通 BenchMod 消费方不会自动加载这些场景。它首先检查正确性，不把帧间隔当作 GPU 时间，
也不以性能相近代替像素正确。

## 运行与复用

要求 Java 25、对应版本的 Minecraft/NeoForge、可用显示器与驱动。先本地发布本分支，
再编译独立示例（生产 JAR 不含 Provider 或 BenchMod 依赖）：

```sh
./gradlew -PmodBenchBinaryArtifacts=true check verifyReleaseReadiness
./gradlew -p examples/graphics-migration-assistant -PmodBenchLocal=true -PmodBenchBinaryArtifacts=true compileBenchJava check
```

在有图形设备的同一机器上分别运行。每次启动会清理当前默认 client 输出，必须在下一次运行前
把**整个 client 目录**另存为新的、不存在的路径（包含 summary.json、原始图像与日志证据）：

```sh
./gradlew -p examples/graphics-migration-assistant -PmodBenchLocal=true -PmodBenchBinaryArtifacts=true \
  -PmodBench.client.graphicsBackend=opengl runBenchClient verifyBenchClientReport
cp -R examples/graphics-migration-assistant/build/modBench/raw-results/default/client /path/to/new-opengl-run
./gradlew -p examples/graphics-migration-assistant -PmodBenchLocal=true -PmodBenchBinaryArtifacts=true \
  -PmodBench.client.graphicsBackend=vulkan runBenchClient verifyBenchClientReport
cp -R examples/graphics-migration-assistant/build/modBench/raw-results/default/client /path/to/new-vulkan-run
python3 tools/graphics_migration_compare.py \
  /path/to/new-opengl-run/artifacts/custom/graphics-migration.json \
  /path/to/new-vulkan-run/artifacts/custom/graphics-migration.json \
  --output /path/to/comparison.json --diff-dir /path/to/comparison-diffs
```

PowerShell 使用 Copy-Item -Recurse 保存目录。机器不具备 Vulkan 时应保留失败/回退证据；
修改报告里的 backend 字段不能替代真实运行。比较工具仅使用 Python 标准库。

在自己的 Mod 的 `src/bench` 中实现 `BenchClientProvider`，于 `registerClient` 调用：

```java
GraphicsMigrationSuite.register(registrar);
```

入口类位于对应版本 API 的 `com.zhongbai233.bench.api.neoforge.graphics`。入口通过 registrar 的游戏 classloader 加载 Runtime 内唯一的 Factory SPI；所有具体 GPU/Minecraft 执行都保留在 Runtime MOD 中，防止 FML 将受保护的游戏类错误加载到普通库 classloader。只从 client Provider
加载；不要在生产代码、专服入口或静态 mod 初始化里访问它。独立示例默认 seed=602263；
接入已有 Provider 时使用其 context.seed，同一对照的 seed 必须一致。可用 Core 中的
`GraphicsMigrationFixtures`、`GraphicsMigrationOracle` 复用源行整理和逐像素参考断言，
为业务 shader/纹理通路注册自己的场景。

## Revision 1 场景与真实断言

所有 required probe 均提交实际 GPU 操作，并在后续 client tick 等待 fence/readback 完成，
再检查完整 RGBA8 原始字节。没有直接 GL handle、PBO 假设、全局 framebuffer 覆写或 CPU 冒充 GPU 的降级。

| 场景 | 操作与失败定位 | Oracle |
| --- | --- | --- |
| rgba-stride-orientation | 17×9 非对称色条、alpha 梯度；非零 source offset、带 padding 的倒序源行整理后上传 | 每个字节精确，覆盖通道顺序/上下颠倒/行宽错误 |
| rg8-uv-channels | 真 RG8_UNORM 上传，经 fragment shader 采样 U/V 到 RGBA8 attachment | R/G 分别对应两通道，B=0/A=255；每通道容差2 |
| buffer-reuse | 同一个 GPU upload buffer 反复 write/copy 到 readback buffer；3轮逐轮完成后复用 | 中间轮使用不同 seed，逐字节检测旧帧/过早复用；最后回归 seed |
| shader-depth-blend | 显式 shader 绑定；蓝色depth=.75，红色alpha=.5/depth=.25，绿色depth=.5 | 深度写入使绿色被拒绝；整幅(128,0,127,255)，容差2 |
| offscreen-copy | 明确 color attachment 渲染采样，再复制到另一纹理 | 独立 target 全 RGBA 精确；检查 attachment、pass结束及copy次序 |
| resource-recreate | 16轮 create→upload→fenced readback→close；每轮变更数据 | 中间读回精确，最后回归 seed；suite所有资源关闭、所有权计数归零 |

尺寸除 buffer-reuse/shader-depth-blend 为8×8外均17×9。禁止坏像素比例为0；容差只允许
指定场景单通道最多2个8-bit量化级。alpha 也参与比较，图像不作自动翻转、颜色校正或裁剪。
原始图像 row 0 是 GPU 原始行约定；PNG 仅按相同顺序展示。

26.2 shader-depth-blend 和纹理场景显式验证 std140 uniform buffer 与 sampler 绑定。
26.3 使用对应 Renderpearl 公共绑定接口。各版本 API 变动限制在 GPU adapter，固定 fixture、
协议及比较规则共享；跨 Minecraft 版本比较会被拒绝。

## 报告、状态与边界

`summary.json` 仍是 Runtime 的权威运行报告；独立 sidecar `artifacts/custom/graphics-migration.json`
是经 `context.artifacts()` 登记 hash/size 的场景证据，schema 为 `graphics-migration/1`。
它记录实际设备 backend/name/vendor/driver、请求 backend、场景 revision/seed/尺寸/阈值、
expected/actual SHA-256、所有权/操作诊断和逐像素误差。每个已读回场景同时保存：

- expected/actual `.rgba`：紧密排列 RGBA8，比较权威数据
- expected/actual `.png`：人工查看
- diff `.png`：包括 alpha 差异的红色热图
- 比较 JSON 与可选 `.ppm`：跨后端差异；驱动字符串分别保存

状态不能混用：

- PASS：六个 required probes 实际执行、读回符合 oracle，且设备和环境有效
- FAIL：GPU 调用/像素/资源清理等断言失败
- BLOCKED：没有设备、请求后端被回退、不支持 required 公共 capability、完成等待超时或场景未执行
- SKIP：仅 optional `engine-resource-reload`，必须有原因；不算已通过

本版不会触发引擎资源重载，明确报告 `engine-resource-reload=SKIP`。
16轮关闭重建和所有权计数只能证明该场景所持资源的生命周期，**不能证明驱动全局无泄漏、
引擎 shader cache 无泄漏或 F3+T reload 正确**。26.2 两个固定 pipeline 由设备缓存持有，
不会为测试清空全局 pipeline cache。后续版本可加入真实 reload gate、业务 shader 和性能采样。

比较器先拒绝不一致 suite/scenario/revision/seed/scene集合/尺寸/阈值、非预期 backend、
不同 Minecraft/device/vendor（仅明确列出的 NVIDIA/AMD/Intel vendor 别名会规范化）、已无效环境、缺失/损坏/hash错误/路径越界的 artifacts，
再验证两个后端各自 oracle 和跨后端像素。driver 字符串可能因 API 不同而变化，独立报告。deviceName 保持严格相等；部分 Mesa/驱动两后端报告不同 renderer 名称时会保守地 INCONCLUSIVE，不会猜测它们是同一物理设备。
只有 required scene 全 PASS 才可形成 PASS 对照；SKIP/BLOCKED/数据不兼容输出 INCONCLUSIVE。
退出码0=PASS、1=FAIL或INCONCLUSIVE、2=调用/输出错误。

## 验证边界

Headless CI 执行 CPU fixture/oracle、suite状态机（含失败/取消/超时/缺设备）、正式 JSON Schema、
比较器负例、真实版本 API 编译、发布与独立消费隔离。它不会伪造 graphics-migration GPU报告。

本工作环境没有显示器/Xvfb/Vulkan ICD，因此 OpenGL/Vulkan 客户端均**未运行**；
编译、JVM测试、shader前端编译与 server smoke 均不能作为真实 GPU PASS。
必须按上面的双后端运行步骤取得实际设备报告后，才可评价迁移或跨后端一致性。
