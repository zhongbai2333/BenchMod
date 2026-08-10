# ADR-0001：Provider 使用 ServiceLoader 发现

- 状态：Accepted
- 日期：2026-07-26

## 背景

消费方的 benchmark 场景位于 `src/bench`，不应进入生产 Mod JAR，也不应为了注册普通场景而创建独立 NeoForge Mod。发现机制必须能在游戏 classloader 中工作，并允许纯 JVM 测试。

## 决策

默认 Provider SPI 为 `ServiceLoader<BenchProvider>`。消费方在 bench resources 中提供 `META-INF/services/<BenchProvider FQCN>`。

Runtime 必须显式传入游戏 classloader、捕获 `ServiceConfigurationError`、按 Provider ID 排序、拒绝空 ID 与重复 ID，并校验预期 Provider 数量。Provider 具有公开无参轻量构造器；Runtime 不跨世界缓存实例。

需要注册 Block、EntityType、Menu、Payload 或 Dimension 等游戏内容时，使用独立 test-only Adapter Mod，而不是扩展默认发现机制。

## 后果

- Core SPI 保持纯 Java，Provider 可直接单元测试；
- 普通场景不污染业务 Mod metadata；
- classpath 缺失和服务描述错误会成为明确的 run failure；
- Provider 构造阶段不能访问尚未就绪的游戏状态。