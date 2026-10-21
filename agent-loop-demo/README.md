# agent-loop-demo（项目二 2.1 产出物二）

Java 手写的最小 Agent 循环：`选择工具 → 执行 → 回传结果 → 继续/终止`。
模型用 **MockLlmClient**（剧本式假模型），不接真 API——目的是先把骨架跑通并留下第一刀 commit；
真模型接入留到工程阶段（6.1 启动门后）换 `LlmClient` 实现，接口签名不变。

## 从 pi 1.3.0 源码学来并落地的约束

| 约束 | 本 demo 的落点 | pi 源码锚点 |
| --- | --- | --- |
| transcript 即唯一真相 | 所有状态都在 `AgentLoop.transcript`，`RunResult.newMessages` 只是本次新增切片 | `types.ts` AgentState 注释 |
| 并行执行但结果保序 | `executeBatch`：`CompletableFuture.allOf` + 预分配数组按索引写入 | `agent-loop.ts:646-654` |
| 终止取全批同意 | `results.stream().allMatch(terminate)` 才停批 | `agent-loop.ts:690` |
| prepare 阶段串行 | 查工具 + 白名单校验先于任何执行，失败直接成错误结果 | `agent-loop.ts:596-617` |
| 任一工具声明串行则整批串行 | `ExecutionMode.SEQUENTIAL` 降级 | `agent-loop.ts:516-522` |
| 工具失败不抛穿 loop | 异常转成 `isError` 结果回灌给模型 | `agent-loop.ts:845-854` |

## 运行

```bash
mvn test          # 8+2 个单元测试
mvn package       # 产出可执行 jar
java -jar target/agent-loop-demo-0.1.0-SNAPSHOT.jar   # 跑一遍演示排查流程
```

## 刻意未覆盖（工程阶段再做，不属于"最小骨架"）

流式输出、`stopReason=length` 整批判失败、steering/followUp 双队列、压缩钩子
（`prepareNextTurn`）、工具集增量 system 消息、工具结果三份内容
（content/details/structuredContent）。这些在机制笔记 §五/§六 有对照结论，
进入诊断服务骨架阶段再实现。
