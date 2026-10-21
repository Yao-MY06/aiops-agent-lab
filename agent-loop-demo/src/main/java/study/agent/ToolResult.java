package study.agent;

public record ToolResult(String content, boolean error, boolean terminate) {

    public static ToolResult ok(String content) {
        return new ToolResult(content, false, false);
    }

    public static ToolResult error(String content) {
        return new ToolResult(content, true, false);
    }

    public static ToolResult terminating(String content) {
        return new ToolResult(content, false, true);
    }
}
