package cz.krokviak.agents.guardrail;

import cz.krokviak.agents.agent.Agent;
import cz.krokviak.agents.context.RunContext;
import cz.krokviak.agents.exception.InputGuardrailTrippedException;
import cz.krokviak.agents.exception.OutputGuardrailTrippedException;
import cz.krokviak.agents.model.*;
import cz.krokviak.agents.runner.AgentLoop;
import cz.krokviak.agents.runner.InputItem;
import cz.krokviak.agents.tool.Tool;
import cz.krokviak.agents.tool.ToolOutput;
import cz.krokviak.agents.tool.Tools;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class ToolGuardrailTest {

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
            public ModelResponseStream callStreamed(LlmContext context, ModelSettings settings) { return null; }
        };
    }

    @Test
    void toolInputGuardrailTripsBeforeToolExecution() {
        AtomicBoolean toolExecuted = new AtomicBoolean(false);

        Tool tool = Tools.function("sensitive_tool")
            .description("A sensitive tool")
            .param("input", String.class, "The input")
            .handler((args, ctx) -> {
                toolExecuted.set(true);
                return ToolOutput.text("executed: " + args.get("input", String.class));
            })
            .build();

        ToolInputGuardrail<Void> guard = ToolInputGuardrail.of("block_sensitive",
            (ctx, data) -> {
                if ("sensitive_tool".equals(data.toolName())) {
                    return GuardrailResult.tripwire("Tool call blocked");
                }
                return GuardrailResult.pass();
            });

        Agent<Void> agent = Agent.<Void>builder("TestAgent")
            .instructions("test")
            .tools(List.of(tool))
            .toolInputGuardrails(List.of(guard))
            .build();

        Model model = toolCallingModel("sensitive_tool", Map.of("input", "data"), "done");

        InputGuardrailTrippedException ex = assertThrows(InputGuardrailTrippedException.class,
            () -> AgentLoop.run(agent, List.of(new InputItem.UserMessage("go")),
                new RunContext<>(null), model, 5));

        assertEquals("block_sensitive", ex.guardrailName());
        assertEquals("Tool call blocked", ex.reason());
        assertFalse(toolExecuted.get(), "Tool should not have been executed");
    }

    @Test
    void toolInputGuardrailPassesAllowsToolExecution() {
        AtomicBoolean toolExecuted = new AtomicBoolean(false);

        Tool tool = Tools.function("safe_tool")
            .description("A safe tool")
            .handler((args, ctx) -> {
                toolExecuted.set(true);
                return ToolOutput.text("ok");
            })
            .build();

        ToolInputGuardrail<Void> guard = ToolInputGuardrail.of("allow_all",
            (ctx, data) -> GuardrailResult.pass());

        Agent<Void> agent = Agent.<Void>builder("TestAgent")
            .instructions("test")
            .tools(List.of(tool))
            .toolInputGuardrails(List.of(guard))
            .build();

        Model model = toolCallingModel("safe_tool", Map.of(), "done");

        AgentLoop.run(agent, List.of(new InputItem.UserMessage("go")),
            new RunContext<>(null), model, 5);

        assertTrue(toolExecuted.get(), "Tool should have been executed");
    }

    @Test
    void toolOutputGuardrailTripsAfterToolExecution() {
        AtomicBoolean toolExecuted = new AtomicBoolean(false);

        Tool tool = Tools.function("output_tool")
            .description("A tool that produces blocked output")
            .handler((args, ctx) -> {
                toolExecuted.set(true);
                return ToolOutput.text("BLOCKED_CONTENT");
            })
            .build();

        ToolOutputGuardrail<Void> guard = ToolOutputGuardrail.of("block_bad_output",
            (ctx, data) -> {
                if (data.output() instanceof ToolOutput.Text t && t.content().contains("BLOCKED")) {
                    return GuardrailResult.tripwire("Blocked output detected");
                }
                return GuardrailResult.pass();
            });

        Agent<Void> agent = Agent.<Void>builder("TestAgent")
            .instructions("test")
            .tools(List.of(tool))
            .toolOutputGuardrails(List.of(guard))
            .build();

        Model model = toolCallingModel("output_tool", Map.of(), "done");

        OutputGuardrailTrippedException ex = assertThrows(OutputGuardrailTrippedException.class,
            () -> AgentLoop.run(agent, List.of(new InputItem.UserMessage("go")),
                new RunContext<>(null), model, 5));

        assertEquals("block_bad_output", ex.guardrailName());
        assertEquals("Blocked output detected", ex.reason());
        assertTrue(toolExecuted.get(), "Tool should have executed before guardrail check");
    }

    @Test
    void toolOutputGuardrailPassesAllowsNormalFlow() {
        Tool tool = Tools.function("clean_tool")
            .description("A tool with clean output")
            .handler((args, ctx) -> ToolOutput.text("clean result"))
            .build();

        ToolOutputGuardrail<Void> guard = ToolOutputGuardrail.of("allow_clean",
            (ctx, data) -> GuardrailResult.pass());

        Agent<Void> agent = Agent.<Void>builder("TestAgent")
            .instructions("test")
            .tools(List.of(tool))
            .toolOutputGuardrails(List.of(guard))
            .build();

        Model model = toolCallingModel("clean_tool", Map.of(), "all good");

        var result = AgentLoop.run(agent, List.of(new InputItem.UserMessage("go")),
            new RunContext<>(null), model, 5);

        assertNotNull(result.finalOutput());
    }

    @Test
    void parallelInputGuardrailsAllPass() {
        AtomicBoolean guard1Checked = new AtomicBoolean(false);
        AtomicBoolean guard2Checked = new AtomicBoolean(false);

        InputGuardrail<Void> guard1 = InputGuardrail.of("guard1", (ctx, data) -> {
            guard1Checked.set(true);
            return GuardrailResult.pass();
        });
        InputGuardrail<Void> guard2 = InputGuardrail.of("guard2", (ctx, data) -> {
            guard2Checked.set(true);
            return GuardrailResult.pass();
        });

        Agent<Void> agent = Agent.<Void>builder("TestAgent")
            .instructions("test")
            .inputGuardrails(List.of(guard1, guard2))
            .build();

        Model model = new Model() {
            @Override
            public ModelResponse call(LlmContext context, ModelSettings settings) {
                return new ModelResponse("r",
                    List.of(new ModelResponse.OutputItem.Message("done")),
                    new Usage(1, 1));
            }
            @Override
            public ModelResponseStream callStreamed(LlmContext context, ModelSettings settings) { return null; }
        };

        AgentLoop.run(agent, List.of(new InputItem.UserMessage("hello")),
            new RunContext<>(null), model, 3);

        assertTrue(guard1Checked.get(), "Guard 1 should have run");
        assertTrue(guard2Checked.get(), "Guard 2 should have run");
    }

    @Test
    void parallelInputGuardrailsOneTrips() {
        InputGuardrail<Void> passGuard = InputGuardrail.of("pass_guard",
            (ctx, data) -> GuardrailResult.pass());
        InputGuardrail<Void> tripGuard = InputGuardrail.of("trip_guard",
            (ctx, data) -> GuardrailResult.tripwire("Tripwire triggered"));

        Agent<Void> agent = Agent.<Void>builder("TestAgent")
            .instructions("test")
            .inputGuardrails(List.of(passGuard, tripGuard))
            .build();

        Model model = new Model() {
            @Override
            public ModelResponse call(LlmContext context, ModelSettings settings) {
                return new ModelResponse("r",
                    List.of(new ModelResponse.OutputItem.Message("done")),
                    new Usage(1, 1));
            }
            @Override
            public ModelResponseStream callStreamed(LlmContext context, ModelSettings settings) { return null; }
        };

        InputGuardrailTrippedException ex = assertThrows(InputGuardrailTrippedException.class,
            () -> AgentLoop.run(agent, List.of(new InputItem.UserMessage("hello")),
                new RunContext<>(null), model, 3));

        assertEquals("trip_guard", ex.guardrailName());
        assertEquals("Tripwire triggered", ex.reason());
    }

    @Test
    void toolCallDataContainsCorrectFields() {
        ToolCallData data = new ToolCallData("my_tool", Map.of("key", "value"));
        assertEquals("my_tool", data.toolName());
        assertEquals("value", data.arguments().get("key"));
    }

    @Test
    void toolOutputDataContainsCorrectFields() {
        ToolOutput output = ToolOutput.text("result");
        ToolOutputData data = new ToolOutputData("my_tool", output);
        assertEquals("my_tool", data.toolName());
        assertEquals(output, data.output());
    }
}
