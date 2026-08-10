# ADR 0006：Runtime 拥有目标构图与机位保持

状态：Accepted

## 背景

消费者过去只能提交玩家 feet pose，并自行猜测 yaw、pitch、FOV、眼高和观察距离。真实 Mod 场景表明，这会产生目标过小、目标画外和高空玩家在截图前坠落等问题；渲染就绪与帧稳定门禁无法证明业务对象已经合理入镜。

## 决策

- Core 用纯 doubles 的 `BenchBounds3`、`BenchCameraFraming` 和 `BenchCameraFramer` 负责确定性透视几何，不依赖 Minecraft 类型。
- 观察方向定义为从相机指向 bounds 中心；`frameFill` 定义为目标最大投影跨度占对应视口维度的比例。
- Core 求解结果是 camera eye pose。NeoForge Runtime 读取实际窗口宽高比、FOV 和玩家眼高，再转换为 `BenchClientPose` feet pose。
- `setPose` 保持原来的一次性语义；需要跨 tick 锁定时显式使用 `holdPose` 或 `holdFramedTarget`，并通过 `BenchPoseHold` 释放。
- Runtime 在 scenario tick 前重施 active hold，并在场景完成、失败或整轮结束时兜底释放。
- 截图继续保存未经裁剪的完整 framebuffer。裁剪不能恢复画外、剔除、LOD 或低像素密度信息，因此不作为取景修复手段。

## 后果

消费者只需提供业务对象的 world-space bounds 和希望观察的方向，不再硬编码眼高与相机距离。高空静态机位具有明确生命周期，不会因重力漂移。几何构图仍不保证无遮挡；目标投影门禁和感知视觉验证作为后续独立能力实现。