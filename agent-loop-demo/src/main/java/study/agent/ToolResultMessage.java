package study.agent;

/**
 * 工具执行结果回灌进 transcript 的消息。
 * error=true 表示失败但不让 loop 崩溃（模型看到错误内容后可自我纠正）；
 * terminate=true 是工具请求终止整批的投票，只有全批都投才生效
 * （pi agent-loop.ts:690 shouldTerminateToolBatch）。
 */
public record ToolResultMessage(String toolCallId, String toolName, String content, boolean error, boolean terminate)
        implements Message {

    @Override
    public String role() {
        return "toolResult";
    }
}
