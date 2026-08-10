# ADR-0003：Gradle Plugin 仅依赖 ModDev 公共 DSL

- 状态：Accepted
- 日期：2026-07-26

## 背景

ModBench Gradle Plugin 需要创建 bench source set 和 ModDev run。依赖内部任务、生成参数文件或反射内部 model 会造成升级脆弱性，并破坏 configuration cache。

## 决策

Plugin 首版支持 ModDevGradle 2.x，只使用公开边界：`ModDevExtension`、`RunModel`、`ModModel`、`addModdingDependenciesTo`、`runs`、`loadedMods`、`sourceSet`、`gameDirectory` 以及公开的参数 DSL。

Plugin 不自行实现 Minecraft `JavaExec` 启动器，不读取 `build/moddev/*Args.txt`，不依赖 internal task 类型、`devLaunchConfig` 或 `MOD_CLASSES`，也不通过反射兼容 ModDev 1.x。

## 后果

- ModDev breaking change 会在清晰的适配层暴露；
- Gradle Plugin 可通过 TestKit 验证 configuration cache；
- ModDev 1.x 需要独立 adapter 或下一个 plugin major；
- run 配置能力受公共 DSL 限制，但换取更稳定的升级路径。