package cz.krokviak.agents.tool;

import cz.krokviak.agents.agent.Agent;
import cz.krokviak.agents.context.RunContext;
import cz.krokviak.agents.exception.ToolExecutionException;
import cz.krokviak.agents.model.*;
import cz.krokviak.agents.runner.AgentLoop;
import cz.krokviak.agents.runner.InputItem;
import cz.krokviak.agents.runner.RunResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ToolIsEnabledTest {

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
    void disabledToolIsExcludedFromDefinitions() {
        FunctionToolImpl disabledTool = Tools.function("disabled_tool")
            .description("A disabled tool")
            .handler((args, ctx) -> ToolOutput.text("should not run"))
            .isEnabled(v -> false)
            .build();

        assertFalse(disabledTool.isEnabled());

        Agent<Void> agent = Agent.<Void>builder("TestAgent")
            .instructions("test")
            .tools(List.of(disabledTool))
            .build();

        // Model that expects no tool calls (returns a message directly)
        Model model = new Model() {
            @Override
            public ModelResponse call(LlmContext context, ModelSettings settings) {
                // Verify no tool definitions were passed
                assertTrue(context.tools().isEmpty(), "Disabled tools should not be in tool definitions");
                return new ModelResponse("r",
                    List.of(new ModelResponse.OutputItem.Message("done")),
                    new Usage(1, 1));
            }
            @Override
            public ModelResponseStream callStreamed(LlmContext context, ModelSettings settings) { return null; }
        };

        RunResult<Void> result = AgentLoop.run(agent,
            List.of(new InputItem.UserMessage("hello")),
            new RunContext<>(null), model, 3);
        assertNotNull(result);
    }

    @Test
    void enabledToolIsIncludedInDefinitions() {
        FunctionToolImpl enabledTool = Tools.function("enabled_tool")
            .description("An enabled tool")
            .handler((args, ctx) -> ToolOutput.text("result"))
            .isEnabled(v -> true)
            .build();

        assertTrue(enabledTool.isEnabled());

        Agent<Void> agent = Agent.<Void>builder("TestAgent")
            .instructions("test")
            .tools(List.of(enabledTool))
            .build();

        Model model = new Model() {
            @Override
            public ModelResponse call(LlmContext context, ModelSettings settings) {
                assertFalse(context.tools().isEmpty(), "Enabled tools should be in tool definitions");
                assertEquals(1, context.tools().size());
                assertEquals("enabled_tool", context.tools().get(0).name());
                return new ModelResponse("r",
                    List.of(new ModelResponse.OutputItem.Message("done")),
                    new Usage(1, 1));
            }
            @Override
            public ModelResponseStream callStreamed(LlmContext context, ModelSettings settings) { return null; }
        };

        AgentLoop.run(agent, List.of(new InputItem.UserMessage("hello")), new RunContext<>(null), model, 3);
    }

    @Test
    void toolWithNoPredicateIsEnabledByDefault() {
        FunctionToolImpl tool = Tools.function("default_tool")
            .description("No predicate set")
            .handler((args, ctx) -> ToolOutput.text("ok"))
            .build();

        assertTrue(tool.isEnabled());
    }

    @Test
    void failureErrorFunctionCatchesExceptionAndReturnsCustomOutput() {
        FunctionToolImpl tool = Tools.function("failing_tool")
            .description("A tool that throws")
            .handler((args, ctx) -> {
                throw new RuntimeException("intentional failure");
            })
            .failureErrorFunction((toolName, ex) ->
                ToolOutput.text("Error in " + toolName + ": " + ex.getMessage()))
            .build();

        ToolOutput output = tool.execute(new ToolArgs(Map.of()), null);
        assertInstanceOf(ToolOutput.Text.class, output);
        assertEquals("Error in failing_tool: intentional failure", ((ToolOutput.Text) output).content());
    }

    @Test
    void withoutFailureErrorFunctionExceptionWrappedAsToolExecutionException() {
        FunctionToolImpl tool = Tools.function("failing_tool")
            .description("A tool that throws")
            .handler((args, ctx) -> {
                throw new RuntimeException("boom");
            })
            .build();

        assertThrows(ToolExecutionException.class,
            () -> tool.execute(new ToolArgs(Map.of()), null));
    }

    @Test
    void enabledToolExecutesNormallyInLoop() {
        FunctionToolImpl tool = Tools.function("greet")
            .description("Greet someone")
            .param("name", String.class, "The name")
            .handler((args, ctx) -> ToolOutput.text("Hello " + args.get("name", String.class)))
            .isEnabled(v -> true)
            .build();

        Agent<Void> agent = Agent.<Void>builder("TestAgent")
            .instructions("test")
            .tools(List.of(tool))
            .build();

        Model model = toolCallingModel("greet", Map.of("name", "Bob"), "Greeted Bob");
        RunResult<Void> result = AgentLoop.run(agent,
            List.of(new InputItem.UserMessage("greet Bob")),
            new RunContext<>(null), model, 10);

        assertNotNull(result.finalOutput());
    }
}
