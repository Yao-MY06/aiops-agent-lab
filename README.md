# 项目二 · 智能故障诊断与自动评测平台（工程根目录）

> 本目录自 **2026-10-20** 起是项目二的唯一代码根目录。
> 计划、验收标准、当天动作、机制笔记**不在这里**，在 Obsidian vault `E:\50-个人系统\`（`02-项目管理/` 存计划、`05-资源库/Agent/` 存笔记）。
>
> **GitHub 仓库：`aiops-agent-lab`（2026-10-28 定：先私有，稳定后再公开）**。公开前必读文末「公开前检查清单」。

## 仓库边界（写死，避免两份真相）

| 内容 | 放哪 | 备注 |
| --- | --- | --- |
| 代码（demo / 将来的诊断服务） | 本仓 | 可复现、可跑测试 |
| 证据（注入记录、原始输出、评测结果） | 本仓 `evidence\<fault-id>\<run-id>\` | 截图只辅助，原始输出为准 |
| 计划、阶段门、验收标准、当天动作 | vault `E:\50-个人系统\` | **不进本仓**，用 README 链接过去 |
| 机制笔记、逆向报告 | vault `05-资源库\Agent\` | 同上 |

## 目录

```
F:\study\agent\
├── README.md                  ← 本文件
├── agent-loop-demo\           ← 2.1 产出物二：Java 最小 Agent loop demo（mock 模型，2026-10-21 完成）
├── evidence\                  ← 证据目录（pi-run-01：2026-10-26 实派任务原始事件流）
├── tools\pi\                  ← pi 1.3.0 运行时（npm 安装结果，只作参照物；已入 .gitignore）
│   └── node_modules\@earendil-works\...
└── reference\pi-1.3.0\        ← 由 source map 还原的原始 TypeScript（读书用，不是可编译工程；MIT，见目录内 LICENSE）
    ├── agent-loop.ts          949 行
    ├── agent.ts               613 行
    └── types.ts               539 行
```

## 迁移记录（2026-10-20 18:1x）

| 项 | 来源 | 去向 |
| --- | --- | --- |
| pi 运行时（7 个包） | `C:\Users\USER\.workbuddy\binaries\node\workspace\node_modules`（WorkBuddy 隔离 npm 目录，129 MB） | `tools\pi\node_modules`（125 MB） |
| 还原源码 3 文件 | `/tmp`（本次会话临时目录） | `reference\pi-1.3.0\` |

迁移后来源目录**未删除**，但项目侧只认 `F:\study\agent`；如需清理，由用户决定。

## 版本登记（实测，`node -e require('package.json')`）

```
@earendil-works/pi-agent-core   1.3.0
@earendil-works/pi-ai           1.3.0
@earendil-works/pi-coding-agent 1.3.0
@earendil-works/pi-codemode     1.3.0
@earendil-works/pi-mcp          1.3.0
@earendil-works/pi-telemetry    1.3.0
@earendil-works/pi-tui          1.3.0
```

上游仓库 `github.com/earendil-works/pi`（原 `badlogic/pi-mono`）。

## `reference/pi-1.3.0` 是怎么来的（方法可复核）

不是从 GitHub 下载的，而是从 **npm 1.3.0 发布包自带的 source map** 里还原的：

```
node_modules/@earendil-works/pi-agent-core/dist/agent-loop.js.map
  → JSON.parse → sourcesContent[0] → 原始的 src/agent-loop.ts
```

与 GitHub `main` 分支同名文件**逐行 diff 无差异**（4 字符差来自行尾空白），所以：

- **`reference/` 里的行号对 1.3.0 发布版有效**，引用时写「函数名 + 行号」；
- 也说明"从 source map 还原源码"是 TypeScript 的标准打包行为 —— 可作 2.2 逆向报告合规声明的同源先例。

## 模型接入（不需要 key 就能确认的部分）

- provider `deepseek` 映射环境变量 `DEEPSEEK_API_KEY`（`pi-ai` 的 `env-api-keys.ts:92`）。
- 内置 model id 只有两个（`pi-ai/dist/providers/data/deepseek.json`）：
  - `deepseek-flash`（DeepSeek V4.1 Flash）
  - `deepseek-v4-pro`（DeepSeek V4 Pro）
  - 两者：`contextWindow 1,000,000`、`maxTokens 384,000`、`reasoning: true`、API 走 `openai-completions`、baseUrl `https://api.deepseek.com`

## 2.1 产出物二：已完成（2026-10-21，经用户授权开工）

`agent-loop-demo\`：Java 手写最小循环「选择工具 → 执行 → 回传 → 继续/终止」，**mock 模型**（不接真 API），
11 个单元测试全绿（`mvn test`），作为自研 Agent 引擎的第一刀 commit 进入本仓库。
三条从 pi 源码学来的硬约束已落地并有测试锚点：transcript 即唯一真相、并行执行但结果保序、终止取"全批同意"。
demo 刻意未覆盖的部分（流式、压缩钩子、双队列等）见 `agent-loop-demo\README.md` 的未覆盖清单。

**2.1 实派任务：证据已在（2026-10-26 修正）**。`evidence\pi-run-01\run1.jsonl`，152 条事件，`cwd=F:\study\agent`，结构为 `agent_start → turn_start ×2 → 1 次 tool_execution_start/end → turn_end → agent_settled`。→ **"没跑过 pi"的说法作废**；2.1 未收口的原因不是缺证据，而是**观察还没写进机制笔记**（该笔记第 2 局的「我的原话」仍空）。

## 许可与来源

| 目录 | 来源 | 许可 | 公开是否可行 |
| --- | --- | --- | --- |
| `agent-loop-demo\`、`evidence\`、本 README | 本项目原创 | 未定（公开前需选一个，建议 MIT 或 Apache-2.0） | 可 |
| `reference\pi-1.3.0\` | `@earendil-works/pi-agent-core@1.3.0` 的 source map 还原 | **MIT（Copyright (c) 2025 Mario Zechner）**，已在目录内附 LICENSE | 可，**但必须随附 LICENSE 原文与版权声明** |
| `tools\` | npm 安装结果 | MIT | 不入库（.gitignore） |

## 公开前检查清单（先私有后公开，公开前逐条过）

- [ ] `reference\pi-1.3.0\LICENSE` 存在且为 MIT 原文（版权方 Mario Zechner）
- [ ] 全仓 grep 本机绝对路径（`F:\study`、`C:\Users\USER`）→ `evidence\` 的 jsonl 里带 `cwd`，需确认可接受或改写成相对路径
- [ ] 全仓 grep 凭据形态（`sk-`、`password`、`secret`、`123321`、`@Love2099`、JWT）
- [ ] **确认没有任何 Claude Code 反混淆源码或其大段摘录进入本仓** —— 只在私有期、只在 vault 里做引用，公开仓最多给"仓库路径 + 行号"这一级引用
- [ ] 选定本仓 LICENSE，并写明 `reference\` 是第三方 MIT 代码、不是自研
- [ ] README 写明哪些是"已验证"、哪些是"计划"，不把计划写成完成
