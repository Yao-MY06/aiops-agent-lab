package study.agent;

import java.util.List;

/**
 * 模型一轮的输出：文本 + 零或多个工具调用。
 * 工具调用为空 = 模型给出最终答复，循环自然终止
 * （pi agent-loop.ts:183 内层循环条件 hasMoreToolCalls）。
 */
public record AssistantMessage(String text, List<ToolCall> toolCalls) implements Message {

    public AssistantMessage {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }

    public static AssistantMessage finalText(String text) {
        return new AssistantMessage(text, List.of());
    }

    public static AssistantMessage ofToolCalls(String text, List<ToolCall> toolCalls) {
        return new AssistantMessage(text, toolCalls);
    }

    public boolean isFinal() {
        return toolCalls.isEmpty();
    }

    @Override
    public String role() {
        return "assistant";
    }
}
