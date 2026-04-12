package cz.krokviak.agents.runner;

import cz.krokviak.agents.def.Agent;
import cz.krokviak.agents.model.*;
import cz.krokviak.agents.session.InMemorySession;
import cz.krokviak.agents.tool.Tools;
import cz.krokviak.agents.tool.ToolOutput;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class RunnerTest {

    private final Model stubModel = new Model() {
        @Override
        public ModelResponse call(LlmContext context, ModelSettings settings) {
            return new ModelResponse("r1",
                List.of(new ModelResponse.OutputItem.Message("Test response")),
                new Usage(10, 20));
        }
        @Override
        public ModelResponseStream callStreamed(LlmContext context, ModelSettings settings) { return null; }
    };

    private final Runner runner = Runner.of(stubModel);

    @Test
    void simpleRunReturnsResult() {
        Agent<Void> agent = Agent.<Void>builder("Test")
            .instructions("You are helpful")
            .build();

        RunResult<Void> result = runner.run(agent, "Hello");
        assertNotNull(result);
        assertNotNull(result.finalOutput());
    }

    @Test
    void runWithSessionPersistsHistory() {
        var session = new InMemorySession();
        Agent<Void> agent = Agent.<Void>builder("Test")
            .instructions("You are helpful")
            .build();

        RunConfig<Void> config = RunConfig.<Void>builder()
            .session(session)
            .sessionId("s1")
            .build();

        runner.run(agent, "First message", config);
        var history = session.getHistory("s1");
        assertFalse(history.isEmpty());
    }

    @Test
    void runStreamedReturnsEventStream() {
        Agent<Void> agent = Agent.<Void>builder("Test")
            .instructions("You are helpful")
            .build();

        var stream = runner.runStreamed(agent, "Hello");
        assertNotNull(stream);
        var result = stream.result();
        assertNotNull(result);
    }
}
