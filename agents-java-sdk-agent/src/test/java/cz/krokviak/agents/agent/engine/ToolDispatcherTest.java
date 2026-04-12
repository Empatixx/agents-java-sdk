package cz.krokviak.agents.agent.engine;

import cz.krokviak.agents.api.event.AgentEvent;
import cz.krokviak.agents.agent.AgentContext;
import cz.krokviak.agents.agent.hook.Hooks;
import cz.krokviak.agents.context.ToolContext;
import cz.krokviak.agents.tool.ExecutableTool;
import cz.krokviak.agents.tool.ToolArgs;
import cz.krokviak.agents.tool.ToolDefinition;
import cz.krokviak.agents.tool.ToolOutput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ToolDispatcherTest {

    AgentContext ctx;
    Hooks hooks;
    List<AgentEvent> events;

    @BeforeEach
    void setup() {
        ctx = new AgentContext(null, "test", null, null,
            null, null, java.nio.file.Path.of("."),
            "", null, null,
            new cz.krokviak.agents.agent.task.TaskManager(),
            new cz.krokviak.agents.agent.mailbox.MailboxManager());
        hooks = new Hooks();
        events = new ArrayList<>();
        ctx.eventBus().subscribe(events::add);
    }

    private ExecutableTool echoTool() {
        return new ExecutableTool() {
            @Override public String name() { return "echo"; }
            @Override public String description() { return "echo input text"; }
            @Override public ToolDefinition definition() {
                return new ToolDefinition("echo", "echo", Map.of());
            }
            @Override public ToolOutput execute(ToolArgs args, ToolContext<?> tc) {
                return ToolOutput.text("echoed: " + args.get("text", String.class));
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
    void unknownToolReturnsError() {
        var dispatcher = new ToolDispatcher(List.of(), hooks, ctx);
        assertTrue(dispatcher.executeTool("nope", Map.of(), "tc-1").startsWith("Error:"));
    }

}
