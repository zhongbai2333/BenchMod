# ADR-0005：Provider 必须由 Runtime 游戏 classloader 发现

- 状态：Accepted
- 日期：2026-07-26

## 背景

NeoForge dev launch 同时存在 application loader 与 FML TRANSFORMER game loader。线程 context classloader 可能指向 application loader。若 ServiceLoader 使用该 loader 创建引用 Minecraft 类型的 Provider，会同时加载两份同名 Minecraft class，并触发 loader constraint violation。

## 决策

Runtime 使用 `ModBenchRuntimeMod.class.getClassLoader()` 作为 Provider 发现 loader。消费方的 bench source set 必须通过 ModDev `ModModel.sourceSet(...)` 绑定到目标 Mod，确保 Provider classes/resources 属于 transformed source folders。

不使用线程 context loader，不把 Provider 作为普通 application classpath library 加载。

## 后果

- Provider 与 Minecraft、NeoForge、Runtime 使用同一游戏 classloader；
- ServiceLoader Provider 可以安全引用 `MinecraftServer` 与 `ServerLevel`；
- Gradle Plugin 必须解析明确的目标 Mod；多 Mod 项目不能依赖容器顺序猜测；
- production JAR 隔离仍由 source set/JAR 检查保证，而不是通过错误的 classloader 隔离实现。