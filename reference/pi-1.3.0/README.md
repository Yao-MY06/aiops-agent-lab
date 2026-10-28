# reference/pi-1.3.0 —— 第三方源码，用于读书与行号引用

**这不是本项目原创代码，是 `@earendil-works/pi-agent-core@1.3.0` 的原始 TypeScript，按 MIT 许可随附。**

| 项 | 值 |
| --- | --- |
| 上游 | https://github.com/earendil-works/pi （原 `badlogic/pi-mono`） |
| 包 | `@earendil-works/pi-agent-core@1.3.0` |
| 许可 | MIT，Copyright (c) 2025 Mario Zechner —— 见同目录 `LICENSE` |
| 还原方式 | 读 `dist/agent-loop.js.map` 的 `sourcesContent[0]`，取回原始 `src/agent-loop.ts`（标准 source map 行为，非破解） |
| 一致性 | 与上游 `main` 分支同名文件逐行 diff 无差异（949 行；4 字符差来自行尾空白） |

## 文件

| 文件 | 行数 | 用途 |
| --- | --- | --- |
| `agent-loop.ts` | 949 | 双层主循环、工具分发、终止判定 |
| `agent.ts` | 613 | Agent 类：状态、两个消息队列、各钩子 |
| `types.ts` | 539 | `AgentState` / `AgentToolResult` 等类型与注释 |

## 引用纪律

- 报告与笔记引用时写「函数名 + 行号」，并注明版本 **1.3.0**；
- **不得把这些文件当成自研成果**写入简历或在 README 中混淆归属；
- 若本项目公开，本 `LICENSE` 必须与这些 `.ts` 文件一起保留（MIT 要求保留版权声明）。
