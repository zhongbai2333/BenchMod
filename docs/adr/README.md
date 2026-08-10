# Architecture Decision Records

| ADR | 决策 | 状态 |
| --- | --- | --- |
| [0001](0001-provider-discovery.md) | Provider 使用 ServiceLoader 发现 | Accepted |
| [0002](0002-report-schema-versioning.md) | JSON 报告与 Schema 独立版本化 | Accepted |
| [0003](0003-moddev-public-api-boundary.md) | Gradle Plugin 仅依赖 ModDev 公共 DSL | Accepted |
| [0004](0004-tick-driven-server-scenarios.md) | 服务端场景使用非阻塞 tick step 契约 | Accepted |
| [0005](0005-game-classloader-provider-discovery.md) | Provider 必须由 Runtime 游戏 classloader 发现 | Accepted |
| [0006](0006-runtime-owned-camera-framing.md) | Runtime 拥有目标构图与机位保持 | Accepted |
| [0007](0007-paired-dedicated-client-orchestration.md) | 外层协调器编排 dedicated server 与 separate client | Accepted |
| [0008](0008-network-impairment-semantics.md) | 网络模拟必须声明 TCP stream、应用消息或 IP packet 语义层 | Accepted |