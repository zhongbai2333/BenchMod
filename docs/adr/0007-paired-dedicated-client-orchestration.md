# ADR 0007：双端专服模式由外层协调器编排

状态：Accepted

## 背景

现有 `runBenchServer` 是独立 dedicated server，`runBenchClient` 是 physical client 加 integrated server。它们不能覆盖只在真实客户端连接专用服务器时出现的登录、同步、延迟、断连和服务端权威问题。

Gradle task 依赖也不能表达“启动 server 并保持运行、等待 ready、并发启动 client、联合退出”。直接依赖 ModDev 内部启动类或拼 Minecraft classpath 会破坏 ADR 0003。

## 决策

- 保留现有两个单端 run 的语义，新增 `runBenchPairedServer` 与 `runBenchRemoteClient`。
- Minecraft 进程仍由 ModDev 公共 `RunModel` 启动；外层 paired coordinator 只监督两个 Gradle invocation，不实现 Minecraft launcher。
- coordinator 在执行期生成 session ID、nonce、端口和 session 专属目录；配置阶段不随机、不占端口。
- client 必须通过 Runtime payload 校验 session/nonce/protocol/scenario 摘要，不能只因端口可连接就接受旧 server。
- server 是 paired phase authority；两端 barrier 均 tick-driven，不阻塞游戏主线程。
- server、client 各自产生权威 participant report，coordinator 另写 paired summary，并保守合并状态。
- 任一 participant、网络 backend 或 barrier 失败都会传播取消；协调器在 `finally` 中回收完整进程树和 backend。

## 后果

双端模式可以重现真实 socket、登录和服务端权威同步路径，同时继续遵守 ModDev 公共边界。代价是需要独立 coordinator、会话协议、远程 client bootstrap、报告关联和 Windows 进程树清理，不能用一个简单聚合 task 冒充。