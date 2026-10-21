package study.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 最小 Agent 循环：选择工具 → 执行 → 回传结果 → 继续/终止。
 * 语义对照 pi 1.3.0（reference/pi-1.3.0/agent-loop.ts）的三条硬约束：
 * transcript 即唯一真相、并行执行但结果保序、终止取全批同意。
 */
public class AgentLoop {

    public record Config(int maxTurns) {

        public Config {
            if (maxTurns < 1) {
                throw new IllegalArgumentException("maxTurns 必须 ≥ 1");
            }
        }

        public static Config defaults() {
            return new Config(10);
        }
    }

    private final LlmClient llm;
    private final List<Tool> tools;
    private final Map<String, Tool> toolsByName;
    private final Config config;
    private final Executor executor;
    private final List<Message> transcript = new ArrayList<>();

    public AgentLoop(LlmClient llm, List<Tool> tools, Config config) {
        this(llm, tools, config, ForkJoinPool.commonPool());
    }

    public AgentLoop(LlmClient llm, List<Tool> tools, Config config, Executor executor) {
        this.llm = Objects.requireNonNull(llm, "llm");
        this.tools = List.copyOf(tools);
        this.toolsByName = this.tools.stream().collect(Collectors.toMap(Tool::name, Function.identity()));
        this.config = Objects.requireNonNull(config, "config");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    /** transcript 是 Agent 状态的唯一真相；对外只给快照。 */
    public List<Message> transcript() {
        return List.copyOf(transcript);
    }

    public RunResult run(String userPrompt) {
        List<Message> newMessages = new ArrayList<>();
        append(new UserMessage(userPrompt), newMessages);

        for (int turn = 1; turn <= config.maxTurns(); turn++) {
            AssistantMessage message = llm.complete(transcript(), tools);
            append(message, newMessages);

            if (message.isFinal()) {
                return new RunResult(RunResult.Status.COMPLETED, message.text(), turn, List.copyOf(newMessages));
            }

            List<ToolResultMessage> results = executeBatch(message.toolCalls());
            results.forEach(r -> append(r, newMessages));

            if (!results.isEmpty() && results.stream().allMatch(ToolResultMessage::terminate)) {
                return new RunResult(RunResult.Status.TERMINATED_BY_TOOLS, null, turn, List.copyOf(newMessages));
            }
        }
        return new RunResult(RunResult.Status.MAX_TURNS_REACHED, null, config.maxTurns(), List.copyOf(newMessages));
    }

    /** prepare 串行 → execute 并行（可降级串行）→ 结果按调用顺序回传。 */
    private List<ToolResultMessage> executeBatch(List<ToolCall> calls) {
        List<Prepared> batch = new ArrayList<>(calls.size());
        for (ToolCall call : calls) {
            Tool tool = toolsByName.get(call.name());
            if (tool == null) {
                batch.add(Prepared.failed(call, "Tool " + call.name() + " not found"));
                continue;
            }
            try {
                tool.validate(call);
                batch.add(Prepared.ready(call, tool));
            } catch (ToolCallException e) {
                batch.add(Prepared.failed(call, e.getMessage()));
            }
        }

        ToolResultMessage[] ordered = new ToolResultMessage[batch.size()];
        boolean sequential = batch.stream()
                .anyMatch(p -> p.tool() != null && p.tool().executionMode() == ExecutionMode.SEQUENTIAL);
        if (sequential) {
            for (int i = 0; i < batch.size(); i++) {
                ordered[i] = executeOne(batch.get(i));
            }
        } else {
            // 保序靠预分配数组按索引写入，而不是 thenApply 收集（收集会按完成先后乱序）
            CompletableFuture<?>[] futures = new CompletableFuture<?>[batch.size()];
            for (int i = 0; i < batch.size(); i++) {
                int index = i;
                futures[i] = CompletableFuture.runAsync(() -> ordered[index] = executeOne(batch.get(index)), executor);
            }
            CompletableFuture.allOf(futures).join();
        }
        return List.of(ordered);
    }

    private ToolResultMessage executeOne(Prepared prepared) {
        ToolCall call = prepared.call();
        if (prepared.immediateError() != null) {
            return new ToolResultMessage(call.id(), call.name(), prepared.immediateError(), true, false);
        }
        try {
            ToolResult result = prepared.tool().execute(call);
            return new ToolResultMessage(call.id(), call.name(), result.content(), result.error(), result.terminate());
        } catch (Exception e) {
            return new ToolResultMessage(call.id(), call.name(), "Tool execution failed: " + e.getMessage(), true, false);
        }
    }

    private void append(Message message, List<Message> newMessages) {
        transcript.add(message);
        newMessages.add(message);
    }

    private record Prepared(ToolCall call, Tool tool, String immediateError) {

        static Prepared ready(ToolCall call, Tool tool) {
            return new Prepared(call, tool, null);
        }

        static Prepared failed(ToolCall call, String error) {
            return new Prepared(call, null, error);
        }
    }
}
