package study.agent;

import java.util.List;

/**
 * LLM 接入的可替换接口（2026-10-20 用户决定：demo 阶段用 mock 模型，不接真 API，
 * 不引入 API key、成本与网络不确定性；工程阶段受 6.1 启动门约束后再换实现，本接口签名不变）。
 * 将来真实实现基于 Spring AI ChatClient（底座红线：Spring AI 只用作接入层，循环自研）。
 */
public interface LlmClient {

    /** 给定完整 transcript 与当前可用工具，返回一轮 assistant 输出。 */
    AssistantMessage complete(List<Message> transcript, List<Tool> tools);
}
