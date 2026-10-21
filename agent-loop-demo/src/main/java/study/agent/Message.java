package study.agent;

/**
 * transcript 中的消息基类。
 * transcript（消息列表）是 Agent 状态的唯一真相，其余状态都是它的投影
 * （pi types.ts AgentState 注释：systemPrompt 都是 "replayed from the transcript"）。
 */
public sealed interface Message permits UserMessage, AssistantMessage, ToolResultMessage {

    String role();
}
