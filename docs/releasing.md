# 1.21.1 publication preflight / 发布预检

本分支开发版本为 `0.1.3-mc1.21.1-beta`，尚未创建 release 或 tag。旧版本线的 JitPack tag 不能复用为 1.21.1 产物。

The source version is `0.1.3-mc1.21.1-beta`. No release or tag has been created; tags from other Minecraft lines do not provide this adapter.

## 源码验证 / Source verification

JDK 21:

```sh
./gradlew -PmodBenchBinaryArtifacts=true check verifyReleaseReadiness
```

`check` 覆盖全部七个模块；`verifyReleaseReadiness` 检查并发布五个公开模块到 Maven Local：

- `bench-api-core`
- `bench-api-neoforge-1.21.1`
- `bench-runtime-neoforge-1.21.1`
- `bench-gradle-plugin`
- `bench-report-schema`

`check` covers all seven modules; the aggregate publication gate covers the five modules above. Network modules remain outside the stable public publication surface.

CI 的主检查使用 `JITPACK=true`、`GROUP=com.github.zhongbai2333`、`ARTIFACT=BenchMod` 和精确 commit SHA 作为 `VERSION`，验证 JitPack 坐标写入。独立消费 job 使用另一台 runner，先发布本分支正常本地坐标，再用独立 Gradle cache 编译示例、验证生产隔离并运行真实专用服务器。

The main CI gate verifies JitPack-style coordinates using the exact commit SHA. The independent-consumer job publishes the current source on a separate runner, compiles with a fresh Gradle cache, checks production isolation, then runs and verifies the actual dedicated server twice.

## 后续正式发布 / Future release

发布需要单独授权。完成客户端图形验证、确认最终 commit 的 CI 和不可变版本号后，方可创建新 tag；不要移动或复用已有 tag。JitPack 产物实际可解析并通过无 `mavenLocal()` 的外部消费方验收后，才能宣称远程版本可用。

A release requires separate authorization. Confirm final-commit CI, graphical-client validation and an immutable version before creating a new tag. Do not move existing tags. Remote availability is established only after JitPack artifacts resolve and an external consumer succeeds without Maven Local.

当前建议使用 [本地消费流程](consumer-quickstart.md)；完整验证边界见 [1.21.1 port](minecraft-1.21.1-port.md)。
