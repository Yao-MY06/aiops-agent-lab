package study.agent;

/** 参数校验/白名单检查失败。prepare 阶段抛出，该调用不进入执行阶段。 */
public class ToolCallException extends Exception {

    public ToolCallException(String message) {
        super(message);
    }
}
