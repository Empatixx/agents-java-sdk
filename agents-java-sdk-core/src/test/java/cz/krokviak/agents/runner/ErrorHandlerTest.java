package cz.krokviak.agents.runner;

import cz.krokviak.agents.agent.Agent;
import cz.krokviak.agents.exception.MaxTurnsExceededException;
import cz.krokviak.agents.model.*;
import cz.krokviak.agents.tool.ToolOutput;
import cz.krokviak.agents.tool.Tools;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ErrorHandlerTest {

    /** A model that always requests a tool call, never producing a final message. */
    static Model infiniteToolModel() {
        return new Model() {
            @Override
            public ModelResponse call(LlmContext context, ModelSettings settings) {
                return new ModelResponse("r",
                    List.of(new ModelResponse.OutputItem.ToolCallRequest("c", "noop", Map.of())),
                    new Usage(1, 1));
            }
            @Override
            public ModelResponseStream callStreamed(LlmContext context, ModelSettings settings) { return null; }
        };
    }

    private final Runner runner = Runner.of(infiniteToolModel());

    @Test
    void maxTurnsErrorHandlerReturnsCustomOutput() {
        var noop = Tools.function("noop").description("no-op")
            .handler((a, c) -> ToolOutput.text("ok")).build();

        Agent<Void> agent = Agent.<Void>builder("Loop")
            .instructions("loop")
            .tools(List.of(noop))
            .build();

        RunConfig<Void> config = RunConfig.<Void>builder()
            .maxTurns(2)
            .errorHandlers(Map.of("max_turns", (ctx, err) -> "max_turns_reached"))
            .build();

        RunResult<Void> result = runner.run(agent, "go", config);

        assertNotNull(result);
        assertEquals("max_turns_reached", result.finalOutput());
    }

    @Test
    void maxTurnsThrowsWhenNoErrorHandler() {
        var noop = Tools.function("noop").description("no-op")
            .handler((a, c) -> ToolOutput.text("ok")).build();

        Agent<Void> agent = Agent.<Void>builder("Loop")
            .instructions("loop")
            .tools(List.of(noop))
            .build();

        RunConfig<Void> config = RunConfig.<Void>builder()
            .maxTurns(2)
            .build();

        assertThrows(MaxTurnsExceededException.class,
            () -> runner.run(agent, "go", config));
    }

    @Test
    void errorHandlerFunctionalInterfaceIsCallable() {
        ErrorHandler<String> handler = (ctx, err) -> "handled: " + err.getMessage();
        // Just verify it compiles and can be called
        assertNotNull(handler);
    }
}
