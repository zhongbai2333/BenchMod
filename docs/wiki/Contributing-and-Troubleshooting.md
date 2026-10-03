# 贡献与排错 / Contributing and troubleshooting

[返回首页 / Home](Home.md)

## 贡献流程 / Contribution workflow

1. 先选目标 Minecraft 分支，阅读对应源码、[架构边界](Versions-and-Architecture.md)和仓库 ADR
2. 保持 Provider / API / Runtime / Gradle 职责分离，使用 ModDev 公共 DSL
3. 为行为变化增加回归测试，保留 production/source JAR 与普通 runtime classpath 隔离
4. 执行该分支的 `check verifyReleaseReadiness` 与独立消费方验收；客户端改动在真实图形环境补验
5. 提交精确命令、commit、配置、报告与已知限制；README/Wiki 同步，不把设计目标写成已完成

Choose the target version branch, preserve module boundaries, add regression coverage, run branch-appropriate checks plus an independent consumer, and report exact evidence and limitations. Client changes need actual graphical validation. Update docs alongside behavior; future plans are not current capabilities.

26.2/26.3 额外检查：

Additional graphics-branch checks:

```sh
./gradlew -PmodBenchBinaryArtifacts=true check verifyReleaseReadiness
python3 -m unittest discover -s tools/tests -v
```

不要为修复移植问题而调用 ModDev 私有 task、反射内部模型、手工解析生成的参数文件或替换 JavaExec。不要为解决 classloader 问题把游戏类型移到错误的普通库 loader。

Do not work around porting issues through private ModDev tasks, reflected internal models, generated-argument parsing or replacement JavaExec launchers. Preserve the correct game classloader boundary.

## 快速排错 / Troubleshooting

| Symptom | Check first |
| --- | --- |
| Plugin/artifact cannot resolve | JitPack plugin mapping and dependency repositories; immutable tag availability; local source was republished; all module versions match |
| Provider missing | Exact ServiceLoader filename and full class name, public no-arg constructor, `src/bench` placement, expected count and game classloader |
| Wrong/missing adapter | Selected branch, NeoForge version and supported module suffix; 26.3 branch does not include the 26.2 adapter |
| No final report | `MODBENCH` log markers, partial report, game log, crash report, timeout/thread dump and bundle |
| Report verification fails | JSON schema/status/runType, expected scenario/metric/artifact IDs, actual per-scenario JFR path |
| Client is inconclusive | Focus, minimization, pause, unexpected screen, resize, readiness and screenshot gate diagnostics |
| GPU suite blocked | Actual device/backend, backend fallback, required capability, fence/readback timeout; never replace evidence with CPU rendering |
| Production package contains bench | Configuration inheritance, `src/main` references, services/resources and ordinary `runtimeClasspath` |
| Paired child cannot resolve dependencies | Forward required non-secret properties through `pairedProjectProperties`; parent `-P` flags are not all inherited |

VS Code/JDT 在改版本后仍引用旧 `build/libs/*.jar`，但 Wrapper 构建通过时，执行 `Java: Clean Java Language Server Workspace` 并刷新 Gradle。不要复制旧 JAR 来掩盖缓存问题。

If VS Code/JDT references old JAR versions after a version change while Wrapper builds succeed, clean the Java language server workspace and refresh Gradle. Do not copy obsolete JARs to hide stale classpaths.

## 发布与验证 / Releases and evidence

`verifyReleaseReadiness` 是本地检查/发布预检，不会自动创建 Git tag、GitHub Release 或保证 JitPack 已构建。正式消费固定不可变 tag；确认新 tag 的远程插件/API/Runtime 均可解析并经独立消费方验收后，再更新示例依赖。不要复用或移动已消费的 tag。

`verifyReleaseReadiness` is a local preflight/publication task. It does not create a Git tag, GitHub Release, or prove that JitPack has built a release. Pin immutable tags, independently verify all remote artifacts, then update consumer versions. Never move a consumed tag.

发布流程见[仓库发布说明](https://github.com/zhongbai2333/BenchMod/blob/main/docs/releasing.md)。版本分支使用其实际模块清单，不能照搬旧文档中的“五个模块”数量。CI 的构建、独立消费方编译和 server smoke 是各自不同的证据；本地或 CI 未执行的客户端测试必须明确标记。

See the [release guide](https://github.com/zhongbai2333/BenchMod/blob/main/docs/releasing.md), but use the actual module list on your branch rather than blindly copying historical module counts. Build, consumer compilation, server smoke and graphical runs provide different evidence. Mark tests that were not run.

## 文档维护 / Maintaining these docs

README 保持简短，教程在 Wiki。`docs/wiki/*.md` 是 Wiki 的版本化源文件；提交修改后同步原生 GitHub Wiki。仓库 Markdown 链接带 `.md`；发布到 GitHub Wiki 时将同 Wiki 页链接改为不带扩展名的页面名。代码、版本配置和可复现报告优先，实施状态与设计文档可能保留历史阶段记录。

Keep READMEs short and tutorials in the Wiki. Versioned sources live in `docs/wiki/*.md`; synchronize the native GitHub Wiki after editing them. Repository-relative page links include `.md`; native Wiki links use page names without the extension. Prefer code, version configuration and reproducible reports over historical stage descriptions.

项目使用 [MIT License](https://github.com/zhongbai2333/BenchMod/blob/main/LICENSE)。贡献请保留许可证与第三方归属信息。

The project is distributed under the [MIT License](https://github.com/zhongbai2333/BenchMod/blob/main/LICENSE). Preserve licensing and third-party attribution when contributing.
