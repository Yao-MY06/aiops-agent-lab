package study.agent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

/** 按预设脚本依次返回响应的模型，用于把 loop 骨架跑通并支撑单元测试。 */
public class MockLlmClient implements LlmClient {

    private final Queue<AssistantMessage> script = new ArrayDeque<>();
    private final List<List<Message>> seenTranscripts = new ArrayList<>();

    public MockLlmClient enqueue(AssistantMessage message) {
        script.add(message);
        return this;
    }

    @Override
    public synchronized AssistantMessage complete(List<Message> transcript, List<Tool> tools) {
        seenTranscripts.add(List.copyOf(transcript));
        AssistantMessage next = script.poll();
        if (next == null) {
            throw new IllegalStateException("MockLlmClient 脚本耗尽：模型被调用的次数超过预设响应数");
        }
        return next;
    }

    /** 每次被调用时看到的 transcript 快照，供测试断言工具结果确实回灌给了模型。 */
    public List<List<Message>> seenTranscripts() {
        return List.copyOf(seenTranscripts);
    }
}
