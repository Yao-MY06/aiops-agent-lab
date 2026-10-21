package study.agent;

import java.util.List;

/**
 * 一次运行的结构化结果。MAX_TURNS_REACHED / TERMINATED_BY_TOOLS 都不伪装成成功，
 * 中间结论保留在 transcript 与 newMessages 里，不静默丢失（实施与验收 §3.1）。
 */
public record RunResult(Status status, String finalText, int turns, List<Message> newMessages) {

    public enum Status {
        /** 模型给出无工具调用的最终答复，自然结束。 */
        COMPLETED,
        /** 达到最大轮次上限。 */
        MAX_TURNS_REACHED,
        /** 整批工具结果都投了 terminate（全批同意）。 */
        TERMINATED_BY_TOOLS
    }
}
