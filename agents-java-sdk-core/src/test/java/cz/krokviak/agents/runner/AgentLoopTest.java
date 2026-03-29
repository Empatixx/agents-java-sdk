package cz.krokviak.agents.runner;

import cz.krokviak.agents.agent.Agent;
import cz.krokviak.agents.context.RunContext;
import cz.krokviak.agents.exception.MaxTurnsExceededException;
import cz.krokviak.agents.model.*;
import cz.krokviak.agents.tool.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class AgentLoopTest {

    static Model simpleModel(String response) {
        return new Model() {
            @Override
            public ModelResponse call(LlmContext context, ModelSettings settings) {
                return new ModelResponse("resp-1",
                    List.of(new ModelResponse.OutputItem.Message(response)),
                    new Usage(10, 20));
            }
            @Override
            public ModelResponseStream callStreamed(LlmContext context, ModelSettings settings) {
                return null;
            }
        };
    }

    static Model toolCallingModel(String toolName, Map<String, Object> args, String finalResponse) {
        return new Model() {
            private int callCount = 0;
            @Override
            public ModelResponse call(LlmContext context, ModelSettings settings) {
                callCount++;
                if (callCount == 1) {
                    return new ModelResponse("resp-1",
                        List.of(new ModelResponse.OutputItem.ToolCallRequest("call-1", toolName, args)),
                        new Usage(10, 20));
                }
                return new ModelResponse("resp-2",
                    List.of(new ModelResponse.OutputItem.Message(finalResponse)),
                    new Usage(5, 10));
            }
            @Override
            public ModelResponseStream callStreamed(LlmContext context, ModelSettings settings) {
                return null;
            }
        };
    }

    @Test
    void simpleAgentReturnsMessage() {
        Agent<Void> agent = Agent.<Void>builder("Test")
            .instructions("You are a test agent")
            .build();

        Model model = simpleModel("Hello World");
        RunContext<Void> ctx = new RunContext<>(null);

        RunResult<Void> result = AgentLoop.run(agent, List.of(new InputItem.UserMessage("hi")), ctx, model, 10);

        assertNotNull(result);
        assertNotNull(result.finalOutput());
    }

    @Test
    void agentWithToolCallsExecutesTool() {
        Tool tool = Tools.function("greet")
            .description("Greet someone")
            .param("name", String.class, "The name")
            .handler((args, tc) -> ToolOutput.text("Hello " + args.get("name", String.class)))
            .build();

        Agent<Void> agent = Agent.<Void>builder("Tooled")
            .instructions("Use tools")
            .tools(List.of(tool))
            .build();

        Model model = toolCallingModel("greet", Map.of("name", "Alice"), "Greeted Alice");
        RunContext<Void> ctx = new RunContext<>(null);

        RunResult<Void> result = AgentLoop.run(agent, List.of(new InputItem.UserMessage("greet Alice")), ctx, model, 10);
        assertNotNull(result.finalOutput());
        assertEquals(3, result.newItems().size()); // tool call + tool output + final message
    }

    @Test
    void maxTurnsThrows() {
        Model infiniteToolModel = new Model() {
            @Override
            public ModelResponse call(LlmContext context, ModelSettings settings) {
                return new ModelResponse("r",
                    List.of(new ModelResponse.OutputItem.ToolCallRequest("c", "noop", Map.of())),
                    new Usage(1, 1));
            }
            @Override
            public ModelResponseStream callStreamed(LlmContext context, ModelSettings settings) { return null; }
        };

        Tool noop = Tools.function("noop").description("no-op").handler((a, c) -> ToolOutput.text("ok")).build();
        Agent<Void> agent = Agent.<Void>builder("Loop")
            .instructions("loop")
            .tools(List.of(noop))
            .build();

        assertThrows(MaxTurnsExceededException.class, () ->
            AgentLoop.run(agent, List.of(new InputItem.UserMessage("go")), new RunContext<>(null), infiniteToolModel, 3));
    }
}
