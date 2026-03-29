package cz.krokviak.agents.streaming;

import cz.krokviak.agents.agent.Agent;
import cz.krokviak.agents.runner.RunItem;
import cz.krokviak.agents.runner.RunResult;
import cz.krokviak.agents.model.Usage;
import cz.krokviak.agents.guardrail.GuardrailResults;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import static org.junit.jupiter.api.Assertions.*;

class StreamEventTest {

    @Test
    void patternMatchingOnEvents() {
        StreamEvent<Void> event = new StreamEvent.TextDeltaEvent<>("Hello");
        String result = switch (event) {
            case StreamEvent.TextDeltaEvent<Void> e -> "text:" + e.delta();
            case StreamEvent.RunItemEvent<Void> e -> "item";
            case StreamEvent.ToolCallEvent<Void> e -> "tool:" + e.toolName();
            case StreamEvent.ToolResultEvent<Void> e -> "result";
            case StreamEvent.AgentUpdatedEvent<Void> e -> "agent";
            case StreamEvent.CompletedEvent<Void> e -> "done";
        };
        assertEquals("text:Hello", result);
    }

    @Test
    void eventStreamIsIterable() {
        var queue = new LinkedBlockingQueue<StreamEvent<Void>>();
        queue.add(new StreamEvent.TextDeltaEvent<>("Hello "));
        queue.add(new StreamEvent.TextDeltaEvent<>("World"));
        queue.add(new StreamEvent.CompletedEvent<>(
            new RunResult<>(null, List.of(), null, List.of(), Usage.zero(), List.of(), GuardrailResults.empty())
        ));

        var stream = new EventStream<Void>(queue);
        var collected = new ArrayList<String>();
        for (StreamEvent<Void> e : stream) {
            if (e instanceof StreamEvent.TextDeltaEvent<Void> td) {
                collected.add(td.delta());
            }
        }
        assertEquals(List.of("Hello ", "World"), collected);
    }

    @Test
    void eventStreamResultBlocksUntilComplete() {
        var queue = new LinkedBlockingQueue<StreamEvent<Void>>();
        var result = new RunResult<Void>(null, List.of(), null, List.of(), Usage.zero(), List.of(), GuardrailResults.empty());
        queue.add(new StreamEvent.CompletedEvent<>(result));

        var stream = new EventStream<Void>(queue);
        assertSame(result, stream.result());
    }
}
