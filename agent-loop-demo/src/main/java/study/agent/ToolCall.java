package study.agent;

import java.util.Map;

/** 模型发出的一次工具调用请求。id 用于把结果消息绑回调用。 */
public record ToolCall(String id, String name, Map<String, Object> arguments) {

    public ToolCall {
        arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
    }
}
