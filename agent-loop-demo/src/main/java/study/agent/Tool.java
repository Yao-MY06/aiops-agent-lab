package study.agent;

public interface Tool {

    String name();

    String description();

    default ExecutionMode executionMode() {
        return ExecutionMode.PARALLEL;
    }

    /**
     * prepare 阶段（串行，先于任何执行）的参数校验/白名单/越权检查。
     * 输入不可信就不执行，宁可返回错误也不产生假证据。
     */
    default void validate(ToolCall call) throws ToolCallException {
    }

    ToolResult execute(ToolCall call) throws Exception;
}
