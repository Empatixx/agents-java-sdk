package cz.krokviak.agents.cli.engine;

import cz.krokviak.agents.api.event.AgentEvent;

import cz.krokviak.agents.cli.CliContext;
import cz.krokviak.agents.cli.event.CliEventBus;
import cz.krokviak.agents.agent.hook.Hooks;
import cz.krokviak.agents.cli.test.FakeRenderer;
import cz.krokviak.agents.context.ToolContext;
import cz.krokviak.agents.runner.InputItem;
import cz.krokviak.agents.runner.RunItem;
import cz.krokviak.agents.tool.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ToolDispatcherTest {

    CliContext ctx;
    Hooks hooks;
    List<AgentEvent> events;

    @BeforeEach
    void setup() {
        var renderer = new FakeRenderer();
        ctx = new CliContext(null, "test", null, null,
            renderer, null, null, java.nio.file.Path.of("."),
            "", null, null, null, null);
        hooks = new Hooks();
        events = new ArrayList<>();
        ctx.eventBus().subscribe(events::add);
    }

    private ExecutableTool echoTool() {
        return new ExecutableTool() {
            @Override public String name() { return "echo"; }
            @Override public String description() { return "echo"; }
            @Override public ToolDefinition definition() {
                return new ToolDefinition("echo", "echo", Map.of());
            }
            @Override public ToolOutput execute(ToolArgs args, ToolContext<?> c) {
                return ToolOutput.text("echoed: " + args.raw().get("text"));
            }
        };
    }

    @Test
    void executeToolReturnsResult() {
        var dispatcher = new ToolDispatcher(List.of(echoTool()), hooks, ctx);
        String result = dispatcher.executeTool("echo", Map.of("text", "hello"), "tc-1");
        assertEquals("echoed: hello", result);
    }

    @Test
    void executeUnknownToolReturnsError() {
        var dispatcher = new ToolDispatcher(List.of(), hooks, ctx);
        String result = dispatcher.executeTool("nonexistent", Map.of(), "tc-1");
        assertTrue(result.startsWith("Error: Unknown tool"));
    }

    @Test
    void executeAllEmitsEvents() {
        var dispatcher = new ToolDispatcher(List.of(echoTool()), hooks, ctx);
        List<RunItem> newItems = new ArrayList<>();
        List<InputItem.ToolCall> toolCalls = List.of(
            new InputItem.ToolCall("tc-1", "echo", Map.of("text", "hi"))
        );

        dispatcher.executeAll(toolCalls, newItems);

        assertTrue(events.stream().anyMatch(e -> e instanceof AgentEvent.ToolStarted ts && ts.name().equals("echo")));
        assertTrue(events.stream().anyMatch(e -> e instanceof AgentEvent.ToolCompleted tc && tc.name().equals("echo")));
        assertEquals(2, newItems.size()); // ToolCallItem + ToolOutputItem
    }

    @Test
    void executeAllAddsToHistory() {
        var dispatcher = new ToolDispatcher(List.of(echoTool()), hooks, ctx);
        List<RunItem> newItems = new ArrayList<>();

        dispatcher.executeAll(
            List.of(new InputItem.ToolCall("tc-1", "echo", Map.of("text", "x"))),
            newItems);

        assertFalse(ctx.history().isEmpty());
        assertTrue(ctx.history().getFirst() instanceof InputItem.ToolResult);
    }

    @Test
    void findReturnsNullForMissing() {
        var dispatcher = new ToolDispatcher(List.of(echoTool()), hooks, ctx);
        assertNull(dispatcher.find("nope"));
        assertNotNull(dispatcher.find("echo"));
    }

    @Test
    void definitionsReturnsAll() {
        var dispatcher = new ToolDispatcher(List.of(echoTool()), hooks, ctx);
        assertEquals(1, dispatcher.definitions().size());
        assertEquals("echo", dispatcher.definitions().getFirst().name());
    }
}
