package study.agent;

/** 批内任一工具声明 SEQUENTIAL，整批降级为串行执行（pi agent-loop.ts:516-522）。 */
public enum ExecutionMode {
    PARALLEL,
    SEQUENTIAL
}
