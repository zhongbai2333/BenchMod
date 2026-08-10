# ADR-0004：服务端场景使用非阻塞 tick step 契约

- 状态：Accepted
- 日期：2026-07-26

## 背景

服务端 stabilize、warmup 和 measure 通常持续多个 tick。同步阻塞 server thread 会冻结游戏；允许 Provider 自建线程则破坏线程归属、取消和采样控制。Phase 0 的同步 `BenchScenario` 只能作为纯 Java 行为原型，不能直接承担 NeoForge Runtime 执行。

## 决策

NeoForge server API 使用 tick 驱动契约。Runtime 在每个 server tick 至多调用一次当前阶段 step，step 返回 `CONTINUE` 或 `COMPLETE`。`setup`、`verify` 和 `teardown` 是一次性 server-thread hook。

Runtime 拥有当前阶段、开始 tick、timeout、取消、指标记录与 teardown 保证；Provider 不阻塞、不创建线程，也不自行推进全局状态机。NeoForge 事件适配器只负责把 server tick 和生命周期事件转发给纯 Java runner。

## 后果

- 跨 tick 等待不会阻塞服务端；
- timeout 与取消可以在统一位置处理；
- runner 可在不启动 Minecraft 的情况下进行确定性单元测试；
- Provider 必须把阶段内进度保存在自己的场景实例中；
- 未来如需更高级等待组合器，可建立在该 step 模型上而不改变 Runtime 所有权。