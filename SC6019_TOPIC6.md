# SC6019 Option 6: Cross-Rollup Intent Router

## Draft project proposal

**Title:** Cross-Rollup Intent Router for Scalable USDC Transfers

**Background and problem.** Users face fragmented execution environments with
different transaction costs, delays and availability. Selecting a chain and bridge
manually adds complexity. This project will let users specify a desired outcome and
constraints, then compare feasible routes across at least two execution
environments. It extends an existing capstone DeFi CLI and agent application with
a course-specific routing and evaluation layer.

**Proposed MVP.** A user submits a structured transfer intent specifying the source
environment, asset, amount, recipient, accepted destination environments, maximum
fee, maximum estimated completion time and preference for cost or speed. The
router queries two explicitly simulated rollup adapters, rejects infeasible or
unavailable routes, and selects the cheapest or fastest feasible route with
deterministic tie-breaking. The frontend displays every candidate, its data source,
estimated fee and delay, rejection reasons, the selected route and execution status.
Execution in the MVP is simulated and is labelled accordingly.

**Reused foundation.** The inherited Go CLI provides protocol access and bridge
quotes. The Java service provides typed CLI integration, REST endpoints, a browser
interface and persistent action states. Its BridgeQuote record already includes
estimated fee and duration. These existing components are a baseline, not new
SC6019 routing results. The course-specific intent schema, comparative router,
route-explanation interface and experiments remain to be implemented.

**Evaluation.** Reproducible workloads will compare the router against always using
environment A and always using environment B. The same intents, load profiles and
random seeds will be used for all policies. The report will measure constraint
satisfaction, failure rate, total simulated fees, mean and p95 completion latency,
and application routing throughput under increasing request loads. It will
separately report decision latency and simulated execution latency. Application
throughput will not be presented as measured blockchain TPS. Results may show
trade-offs rather than improvements in every scenario.

**Scope and trust assumptions.** Simulation is allowed by Option 6. The MVP does
not require a new rollup deployment, a custom wallet or live mainnet transfers.
Users must explicitly permit candidate destinations; the router cannot change
the desired outcome. Quotes and availability information are inputs that can be
stale or wrong. The report will discuss centralized route selection, adapter trust,
recovery after partial failures and what remains untested on real networks. Live
quote adapters and wallet-confirmed execution are optional extensions.

## 中文：选题依据

所提供的《SC6019 Project Description.pdf》第 11–12 页规定：

1. 设计用户意图接口，表达支付、兑换或资产转移等目标。
2. 在至少两个真实或模拟执行环境之间选路。
3. 使用费用、延迟、拥堵或成功概率等选路指标。
4. 前端显示路由选择与执行状态。
5. 分析用户可扩展性与系统效率，并讨论路由器的信任和中心化问题。

本仓库选择其中的资产转移场景，以费用和延迟作为 MVP 指标。AI 可以
辅助解析用户请求和解释路线，路线可行性检查与排序由确定性的程序完成。

## 已有基础与 SC6019 新增工作

| 课程要求 | 已有可复用基础 | 需要新增的工作 |
|---|---|---|
| 高层用户意图 | 自然语言聊天、现有 Intent 解析 | 资产转移目标、允许的目的环境、费用和时限约束 |
| 至少两个执行环境 | CLI 多链能力、按指定提供方获取 bridge quote | 双环境适配接口、模拟环境和候选路线枚举 |
| 路由标准 | BridgeQuote 的 estimatedFeeUsd、estimatedTimeS 字段 | 成本口径、可行性过滤、费用或速度优先策略 |
| 前端与状态 | 聊天 UI、持久化 Action 状态 | 候选路线对比、拒绝理由、选择原因、路由步骤状态 |
| 可扩展性分析 | 单元测试、异步执行基础 | 可复现实验、固定路线基线、负载与失败场景分析 |

已有最高 APY 推荐和单条 bridge quote 查询不能直接当作完整的跨 Rollup
路由器。导入新仓库建立课程开发基础，尚未完成上述新增模块。

## MVP 意图示例

用户希望从指定源环境发送 100 USDC，收款方接受两个目的环境，要求费用
不超过 2 美元、预计完成时间不超过 120 秒，并优先选择费用更低的路线。

```json
{
  "operation": "transfer",
  "sourceEnvironment": "source_env",
  "asset": "USDC",
  "amountBaseUnits": "100000000",
  "assetDecimals": 6,
  "recipient": "<recipient-address>",
  "allowedDestinationEnvironments": ["rollup_a", "rollup_b"],
  "maxFeeUsd": "2.00",
  "maxEstimatedCompletionSeconds": 120,
  "preference": "lowest_cost"
}
```

`rollup_a` 与 `rollup_b` 是明确标注的模拟环境，不代表已经测量的真实网络。
收款方接受的链必须事先确定；如果用户指定唯一目的链，路由器须遵守该限制。

## 路由逻辑与数据约定

- 枚举受支持、被用户允许的路线，检查资产、金额和接收地址。
- 统一比较完整路线的费用和预计完成时间，明确包含哪些费用项目。
- 未知费用、过期报价或不可用路线不能当作零费用成功路线。
- 先过滤费用、时限和可用性约束，再按用户选择的费用或速度排序。
- 使用固定的次要排序指标和路线 ID 处理并列结果，便于复现。
- 没有可行路线时返回明确原因，不悄悄放宽用户条件。
- 记录每条候选路线、选择理由、数据来源与模拟标识。

## 建议的开发里程碑

| 阶段 | 可验收交付 |
|---|---|
| 1：接口与环境 | Intent、RouteCandidate、RouteDecision 数据结构；两个可配置模拟适配器 |
| 2：确定性选路 | 约束过滤、费用与速度策略；过期、失败、无可行路线和并列情况测试 |
| 3：用户界面 | 输入目标、比较候选路线、展示选择理由和模拟执行进度 |
| 4：对比实验 | 固定 A、固定 B、路由器三个策略；相同负载和随机种子；导出结果与图表 |
| 5：报告 | 架构、正确性、费用／延迟权衡、中心化与信任假设、真实集成局限 |

## 来源与提交记录

capstone 和原项目的来源保留在 [ORIGINAL_SOURCE.md](ORIGINAL_SOURCE.md)。
新的提交历史记录导入及之后的 SC6019 工作，课程报告须说明复用基础和新增贡献。
提交次数本身不代表代码是本课程重新编写的。

以上是选题与实施计划，不是已完成的功能声明或实验结果。
