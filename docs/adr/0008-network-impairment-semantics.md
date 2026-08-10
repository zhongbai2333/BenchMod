# ADR 0008：网络模拟必须声明故障语义层

状态：Accepted

## 背景

Minecraft 使用 TCP。用户态代理若随机丢弃、交换或复制已经读取的 stream bytes，只会破坏协议帧；它不会触发发送端 TCP segment 重传，也不等价于真实网络丢包、乱序或重复。

## 决策

网络 profile 必须声明实际语义层：

- `TCP_STREAM`：跨平台用户态代理；当前默认 backend 支持方向性固定延迟、确定性抖动、带宽、转发暂停和 abortive 连接中止。blackhole、半关闭与真正发生在 TCP handshake 阶段的拒绝连接必须由明确宣告对应 capability 的后续 backend 提供，默认代理不得以 accept 后 close 冒充拒绝连接。
- `APPLICATION_MESSAGE`：明确的 Minecraft/Mod payload 故障；可做逻辑消息 delay/drop/reorder/duplicate，但不得称为 IP 丢包。
- `IP_PACKET`：原生 packet backend；可做 packet loss/reorder/duplicate，通常需要 `tc netem`、WinDivert 或等价特权能力。

默认 Windows 无管理员路径使用 ModBench TCP stream proxy。profile 在启动任何 Minecraft 进程前与 backend capability 协商；缺能力返回 `INCOMPATIBLE`，不得静默忽略或近似执行。

每个 profile 固定根 seed、规范化 SHA-256 和派生算法。上下行、warmup/measure、每个 event 使用独立随机流；随机决策不得依赖线程竞争或 socket `read()` 次数。

## 指标语义

报告区分三类数据：

- requested/configured impairment；
- backend observed queue、delay、bytes 和 event timing；
- game/application observed RTT、revision sync、frame 与 tick 指标。

配置每方向 100 ms delay 不代表应用 ping 精确为 200 ms，额外 RTT 还包含 tick、handler、调度和 OS 网络栈。

## 后果

默认代理能可靠覆盖高 ping、抖动、低带宽、短时卡顿和已有连接中止，却不会对外虚假宣称真实丢包或握手拒绝。真实 packet loss 和 handshake refusal 作为更低层 backend 能力单独验证、单独报告。