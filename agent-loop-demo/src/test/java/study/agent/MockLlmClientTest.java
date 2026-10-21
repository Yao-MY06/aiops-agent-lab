package study.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class MockLlmClientTest {

    @Test
    void exhaustedScriptThrows() {
        MockLlmClient mock = new MockLlmClient();
        assertThrows(IllegalStateException.class, () -> mock.complete(List.of(), List.of()));
    }

    @Test
    void recordsEveryTranscriptItSaw() {
        MockLlmClient mock = new MockLlmClient()
                .enqueue(AssistantMessage.finalText("一"))
                .enqueue(AssistantMessage.finalText("二"));
        mock.complete(List.of(new UserMessage("甲")), List.of());
        mock.complete(List.of(new UserMessage("甲"), new UserMessage("乙")), List.of());

        assertEquals(2, mock.seenTranscripts().size());
        assertEquals(1, mock.seenTranscripts().get(0).size());
        assertEquals(2, mock.seenTranscripts().get(1).size());
    }
}
