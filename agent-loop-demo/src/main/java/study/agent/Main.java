package study.agent;

import java.util.List;
import java.util.Map;
import study.agent.tools.QueryMetricsTool;
import study.agent.tools.SearchLogsTool;

/**
 * 演示：mock 模型脚本驱动一次"告警排查"——先查指标，再查日志，最后给结论。
 * 模型是剧本，loop 骨架是真的；换成真模型只需实现 LlmClient。
 */
public class Main {

    public static void main(String[] args) {
        MockLlmClient mock = new MockLlmClient()
                .enqueue(AssistantMessage.ofToolCalls("先看 order-service 的 RT 指标", List.of(
                        new ToolCall("tc-1", "query_metrics",
                                Map.of("service", "order-service", "metric", "rt_ms")))))
                .enqueue(AssistantMessage.ofToolCalls("RT 异常，再查错误日志定位", List.of(
                        new ToolCall("tc-2", "search_logs",
                                Map.of("service", "order-service", "keyword", "ERROR", "limit", 20)))))
                .enqueue(AssistantMessage.finalText(
                        "根因：order-service 存在慢 SQL 并伴随连接池等待，导致接口 RT 飙升。"
                                + "证据：tc-1（p99 4360ms，基线 35ms）、tc-2（Slow SQL 日志，traceId=tr-8f21）。"
                                + "置信度：中（演示数据）。"));

        AgentLoop loop = new AgentLoop(
                mock,
                List.of(new QueryMetricsTool(), new SearchLogsTool()),
                AgentLoop.Config.defaults());

        RunResult result = loop.run("告警：order-service 接口 RT 飙升，请排查根因");

        int turn = 0;
        for (Message message : result.newMessages()) {
            switch (message) {
                case UserMessage u -> System.out.println("[user] " + u.text());
                case AssistantMessage a -> {
                    turn++;
                    System.out.println("[assistant · turn " + turn + "] " + (a.text() == null ? "" : a.text()));
                    for (ToolCall call : a.toolCalls()) {
                        System.out.println("  -> 调用工具 " + call.name() + " " + call.arguments() + " (" + call.id() + ")");
                    }
                }
                case ToolResultMessage r -> {
                    System.out.println("[toolResult <- " + r.toolCallId() + "]" + (r.error() ? " (ERROR)" : ""));
                    System.out.println(r.content().indent(2).stripTrailing());
                }
            }
        }
        System.out.println("\n状态: " + result.status() + "，轮次: " + result.turns());
    }
}
