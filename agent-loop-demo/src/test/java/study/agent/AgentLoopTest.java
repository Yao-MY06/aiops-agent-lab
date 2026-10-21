package study.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import study.agent.tools.QueryMetricsTool;
import study.agent.tools.SearchLogsTool;

class AgentLoopTest {

    private static Tool stubTool(String name, long sleepMillis, ToolResult result) {
        return new Tool() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public String description() {
                return "stub " + name;
            }

            @Override
            public ToolResult execute(ToolCall call) throws Exception {
                if (sleepMillis > 0) {
                    Thread.sleep(sleepMillis);
                }
                return result;
            }
        };
    }

    @Test
    void fullCycleFeedsToolResultsBackIntoTranscript() {
        QueryMetricsTool metrics = new QueryMetricsTool();
        MockLlmClient mock = new MockLlmClient()
                .enqueue(AssistantMessage.ofToolCalls("先查指标", List.of(
                        new ToolCall("tc-1", "query_metrics", Map.of("service", "order-service", "metric", "rt_ms")))))
                .enqueue(AssistantMessage.ofToolCalls("再查日志", List.of(
                        new ToolCall("tc-2", "search_logs",
                                Map.of("service", "order-service", "keyword", "ERROR", "limit", 20)))))
                .enqueue(AssistantMessage.finalText("根因：慢 SQL"));

        AgentLoop loop = new AgentLoop(mock, List.of(metrics, new SearchLogsTool()), AgentLoop.Config.defaults());
        RunResult result = loop.run("排查 RT 飙升");

        assertEquals(RunResult.Status.COMPLETED, result.status());
        assertEquals(3, result.turns());
        assertEquals("根因：慢 SQL", result.finalText());

        List<String> roles = loop.transcript().stream().map(Message::role).toList();
        assertEquals(
                List.of("user", "assistant", "toolResult", "assistant", "toolResult", "assistant"), roles);

        // 模型第三轮看到的 transcript 必须包含前两轮的工具结果——回传闭环成立
        List<Message> thirdCallView = mock.seenTranscripts().get(2);
        List<String> thirdCallRoles = thirdCallView.stream().map(Message::role).toList();
        assertEquals(List.of("user", "assistant", "toolResult", "assistant", "toolResult"), thirdCallRoles);
    }

    @Test
    void naturalStopWhenAssistantHasNoToolCalls() {
        QueryMetricsTool metrics = new QueryMetricsTool();
        MockLlmClient mock = new MockLlmClient().enqueue(AssistantMessage.finalText("直接给结论"));

        AgentLoop loop = new AgentLoop(mock, List.of(metrics), AgentLoop.Config.defaults());
        RunResult result = loop.run("无需工具的提问");

        assertEquals(RunResult.Status.COMPLETED, result.status());
        assertEquals(1, result.turns());
        assertEquals(0, metrics.executions());
    }

    @Test
    void maxTurnsReturnsIncompleteAndKeepsTranscript() {
        MockLlmClient mock = new MockLlmClient()
                .enqueue(AssistantMessage.ofToolCalls("再查一次", List.of(
                        new ToolCall("tc-1", "query_metrics", Map.of("service", "order-service", "metric", "rt_ms")))))
                .enqueue(AssistantMessage.ofToolCalls("还查一次", List.of(
                        new ToolCall("tc-2", "query_metrics", Map.of("service", "order-service", "metric", "cpu_usage")))));

        AgentLoop loop = new AgentLoop(mock, List.of(new QueryMetricsTool()), new AgentLoop.Config(2));
        RunResult result = loop.run("永远查不完的任务");

        assertEquals(RunResult.Status.MAX_TURNS_REACHED, result.status());
        assertEquals(2, result.turns());
        // 触顶不静默丢失：user + 2×(assistant+toolResult) 全部留痕
        assertEquals(5, loop.transcript().size());
        assertEquals(5, result.newMessages().size());
    }

    @Test
    void parallelBatchKeepsResultsInCallOrder() {
        Tool slow = stubTool("slow_tool", 300, ToolResult.ok("slow done"));
        Tool fast = stubTool("fast_tool", 0, ToolResult.ok("fast done"));
        MockLlmClient mock = new MockLlmClient()
                .enqueue(AssistantMessage.ofToolCalls("同批两个调用", List.of(
                        new ToolCall("tc-slow", "slow_tool", Map.of()),
                        new ToolCall("tc-fast", "fast_tool", Map.of()))))
                .enqueue(AssistantMessage.finalText("完成"));

        AgentLoop loop = new AgentLoop(mock, List.of(slow, fast), AgentLoop.Config.defaults());
        RunResult result = loop.run("触发并行批");

        assertEquals(RunResult.Status.COMPLETED, result.status());
        List<ToolResultMessage> toolResults = loop.transcript().stream()
                .filter(ToolResultMessage.class::isInstance)
                .map(ToolResultMessage.class::cast)
                .toList();
        assertEquals(2, toolResults.size());
        // fast 先完成，但回灌顺序必须仍是调用顺序 tc-slow → tc-fast
        assertEquals("tc-slow", toolResults.get(0).toolCallId());
        assertEquals("tc-fast", toolResults.get(1).toolCallId());
    }

    @Test
    void sequentialModeDowngradesWholeBatch() {
        StringBuilder executionLog = new StringBuilder();
        Tool sequential = new Tool() {
            @Override
            public String name() {
                return "sequential_tool";
            }

            @Override
            public String description() {
                return "stub";
            }

            @Override
            public ExecutionMode executionMode() {
                return ExecutionMode.SEQUENTIAL;
            }

            @Override
            public ToolResult execute(ToolCall call) {
                executionLog.append("A");
                return ToolResult.ok("A done");
            }
        };
        Tool parallel = new Tool() {
            @Override
            public String name() {
                return "parallel_tool";
            }

            @Override
            public String description() {
                return "stub";
            }

            @Override
            public ToolResult execute(ToolCall call) throws Exception {
                Thread.sleep(50);
                executionLog.append("B");
                return ToolResult.ok("B done");
            }
        };
        MockLlmClient mock = new MockLlmClient()
                .enqueue(AssistantMessage.ofToolCalls("同批含串行工具", List.of(
                        new ToolCall("tc-a", "sequential_tool", Map.of()),
                        new ToolCall("tc-b", "parallel_tool", Map.of()))))
                .enqueue(AssistantMessage.finalText("完成"));

        AgentLoop loop = new AgentLoop(mock, List.of(sequential, parallel), AgentLoop.Config.defaults());
        RunResult result = loop.run("触发串行降级");

        assertEquals(RunResult.Status.COMPLETED, result.status());
        assertEquals("AB", executionLog.toString());
    }

    @Test
    void unknownToolBecomesErrorResultAndLoopContinues() {
        MockLlmClient mock = new MockLlmClient()
                .enqueue(AssistantMessage.ofToolCalls("调用不存在的工具", List.of(
                        new ToolCall("tc-x", "nope_tool", Map.of()))))
                .enqueue(AssistantMessage.finalText("认错并给结论"));

        AgentLoop loop = new AgentLoop(mock, List.of(new QueryMetricsTool()), AgentLoop.Config.defaults());
        RunResult result = loop.run("触发未知工具");

        assertEquals(RunResult.Status.COMPLETED, result.status());
        ToolResultMessage errorResult = loop.transcript().stream()
                .filter(ToolResultMessage.class::isInstance)
                .map(ToolResultMessage.class::cast)
                .findFirst()
                .orElseThrow();
        assertTrue(errorResult.error());
        assertTrue(errorResult.content().contains("nope_tool not found"));
    }

    @Test
    void validationFailureSkipsExecutionButFeedsErrorBack() {
        QueryMetricsTool metrics = new QueryMetricsTool();
        MockLlmClient mock = new MockLlmClient()
                .enqueue(AssistantMessage.ofToolCalls("查一个白名单外的服务", List.of(
                        new ToolCall("tc-bad", "query_metrics", Map.of("service", "evil-service", "metric", "rt_ms")))))
                .enqueue(AssistantMessage.finalText("参数越权，改给有限结论"));

        AgentLoop loop = new AgentLoop(mock, List.of(metrics), AgentLoop.Config.defaults());
        RunResult result = loop.run("触发白名单拦截");

        assertEquals(RunResult.Status.COMPLETED, result.status());
        assertEquals(0, metrics.executions());
        ToolResultMessage errorResult = loop.transcript().stream()
                .filter(ToolResultMessage.class::isInstance)
                .map(ToolResultMessage.class::cast)
                .findFirst()
                .orElseThrow();
        assertTrue(errorResult.error());
        assertTrue(errorResult.content().contains("白名单"));
    }

    @Test
    void terminateRequiresWholeBatchConsent() {
        Tool terminating = stubTool("terminating_tool", 0, ToolResult.terminating("致命线索，请求终止"));
        Tool normal = stubTool("normal_tool", 0, ToolResult.ok("正常结果"));

        MockLlmClient bothTerminate = new MockLlmClient()
                .enqueue(AssistantMessage.ofToolCalls("两个都投终止", List.of(
                        new ToolCall("tc-1", "terminating_tool", Map.of()),
                        new ToolCall("tc-2", "terminating_tool", Map.of()))));
        AgentLoop loopA = new AgentLoop(bothTerminate, List.of(terminating, normal), AgentLoop.Config.defaults());
        RunResult resultA = loopA.run("全批同意场景");
        assertEquals(RunResult.Status.TERMINATED_BY_TOOLS, resultA.status());
        assertEquals(1, resultA.turns());

        MockLlmClient mixed = new MockLlmClient()
                .enqueue(AssistantMessage.ofToolCalls("只有一个投终止", List.of(
                        new ToolCall("tc-1", "terminating_tool", Map.of()),
                        new ToolCall("tc-2", "normal_tool", Map.of()))))
                .enqueue(AssistantMessage.finalText("单个工具喊停不足以停批，继续给结论"));
        AgentLoop loopB = new AgentLoop(mixed, List.of(terminating, normal), AgentLoop.Config.defaults());
        RunResult resultB = loopB.run("单票场景");
        assertEquals(RunResult.Status.COMPLETED, resultB.status());
        assertEquals(2, resultB.turns());
    }

    @Test
    void toolExceptionBecomesErrorResultInsteadOfCrashingLoop() {
        MockLlmClient mock = new MockLlmClient()
                .enqueue(AssistantMessage.ofToolCalls("调用会炸的工具", List.of(
                        new ToolCall("tc-boom", "exploding_tool", Map.of()))))
                .enqueue(AssistantMessage.finalText("工具炸了，降级给结论"));

        AgentLoop loop = new AgentLoop(mock, List.of(new Tool() {
            @Override
            public String name() {
                return "exploding_tool";
            }

            @Override
            public String description() {
                return "stub";
            }

            @Override
            public ToolResult execute(ToolCall call) {
                throw new RuntimeException("boom");
            }
        }), AgentLoop.Config.defaults());
        RunResult result = loop.run("触发工具异常");

        assertEquals(RunResult.Status.COMPLETED, result.status());
        ToolResultMessage errorResult = loop.transcript().stream()
                .filter(ToolResultMessage.class::isInstance)
                .map(ToolResultMessage.class::cast)
                .findFirst()
                .orElseThrow();
        assertTrue(errorResult.error());
        assertTrue(errorResult.content().contains("boom"));
        assertFalse(errorResult.terminate());
    }
}
