# ADR-0002：JSON 报告与 Schema 独立版本化

- 状态：Accepted
- 日期：2026-07-26

## 背景

报告需要被 CI、baseline 比较器和未来 dashboard 长期消费。Runtime 发布节奏受 Minecraft 开发线约束，而报告读取器不应被迫同频升级。

## 决策

JSON 是权威报告格式，Schema 使用独立 SemVer。新增可选字段提升 minor；删除字段、改名、语义变化或单位变化提升 major。读取器必须忽略未知字段。

顶层固定包含 `schema`、`run`、`environment`、`artifacts`、`providers`、`scenarios`、`summary`、`diagnostics`。JUnit XML 和 Markdown 仅为派生视图，不承载 JSON 中不存在的权威信息。

首个原型 Schema 为 `1.0.0`，采用 JSON Schema Draft 2020-12。

## 后果

- 不同 Runtime 版本可产生同一 Schema major 的报告；
- CI 可在不启动 Minecraft 的情况下验证产物；
- 单位或字段语义修改需要显式迁移规则；
- Schema 模块必须包含可验证样例。