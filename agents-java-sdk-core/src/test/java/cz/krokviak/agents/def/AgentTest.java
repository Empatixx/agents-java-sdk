package cz.krokviak.agents.def;

import cz.krokviak.agents.tool.Tool;
import cz.krokviak.agents.tool.Tools;
import cz.krokviak.agents.tool.ToolOutput;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AgentTest {

    @Test
    void builderCreatesImmutableAgent() {
        Agent<Void> agent = Agent.<Void>builder("TestAgent")
            .instructions("You are a test agent")
            .model("gpt-4.1")
            .build();

        assertEquals("TestAgent", agent.name());
        assertEquals("You are a test agent", agent.instructions());
        assertEquals("gpt-4.1", agent.model());
        assertTrue(agent.tools().isEmpty());
        assertTrue(agent.handoffs().isEmpty());
    }

    @Test
    void agentIsImmutable() {
        Agent<Void> agent = Agent.<Void>builder("Test")
            .instructions("test")
            .build();

        assertThrows(UnsupportedOperationException.class, () -> agent.tools().add(null));
    }

    @Test
    void toBuilderClones() {
        Agent<Void> original = Agent.<Void>builder("Original")
            .instructions("original instructions")
            .model("gpt-4.1")
            .build();

        Agent<Void> modified = original.toBuilder()
            .name("Modified")
            .instructions("modified instructions")
            .build();

        assertEquals("Original", original.name());
        assertEquals("Modified", modified.name());
        assertEquals("gpt-4.1", modified.model());
    }

    @Test
    void agentWithTools() {
        Tool tool = Tools.function("test")
            .description("test tool")
            .handler((args, ctx) -> ToolOutput.text("ok"))
            .build();

        Agent<Void> agent = Agent.<Void>builder("Tooled")
            .instructions("use tools")
            .tools(List.of(tool))
            .build();

        assertEquals(1, agent.tools().size());
        assertEquals("test", agent.tools().getFirst().name());
    }

    @Test
    void agentAsToolCreatesAgentTool() {
        Agent<Void> agent = Agent.<Void>builder("Specialist")
            .instructions("I specialize in X")
            .build();

        Tool asTool = agent.asTool("A specialist for X tasks");
        assertEquals("transfer_to_Specialist", asTool.name());
        assertEquals("A specialist for X tasks", asTool.description());
    }

    @Test
    void defaultToolUseBehavior() {
        Agent<Void> agent = Agent.<Void>builder("Test")
            .instructions("test")
            .build();

        assertEquals(ToolUseBehavior.RUN_LLM_AGAIN, agent.toolUseBehavior());
    }

    @Test
    void dynamicInstructions() {
        Agent<String> agent = Agent.<String>builder("Dynamic")
            .dynamicInstructions((ctx, a) -> "Hello " + ctx.context())
            .build();

        assertNotNull(agent.dynamicInstructions());
        assertNull(agent.instructions());
    }
}
