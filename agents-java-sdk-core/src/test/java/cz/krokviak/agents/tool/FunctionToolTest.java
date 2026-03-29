package cz.krokviak.agents.tool;

import cz.krokviak.agents.context.RunContext;
import cz.krokviak.agents.context.ToolContext;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class FunctionToolTest {

    static class SampleTools {
        @FunctionTool(name = "greet", description = "Greet a person")
        public ToolOutput greet(@Param("person name") String name) {
            return ToolOutput.text("Hello, " + name + "!");
        }

        @FunctionTool(description = "Add two numbers")
        public ToolOutput add(@Param("first number") int a, @Param("second number") int b) {
            return ToolOutput.text(String.valueOf(a + b));
        }

        @FunctionTool(description = "Uses tool context")
        public ToolOutput withContext(@Param("input") String input, ToolContext<?> ctx) {
            return ToolOutput.text("call:" + ctx.toolCallId() + " input:" + input);
        }
    }

    @Test
    void fromClassFindsAnnotatedMethods() {
        var tools = Tools.fromClass(new SampleTools());
        assertEquals(3, tools.size());
    }

    @Test
    void toolHasCorrectNameAndDescription() {
        var tools = Tools.fromClass(new SampleTools());
        var greet = tools.stream().filter(t -> t.name().equals("greet")).findFirst().orElseThrow();
        assertEquals("greet", greet.name());
        assertEquals("Greet a person", greet.description());
    }

    @Test
    void toolNameDefaultsToMethodName() {
        var tools = Tools.fromClass(new SampleTools());
        var add = tools.stream().filter(t -> t.name().equals("add")).findFirst().orElseThrow();
        assertNotNull(add);
    }

    @Test
    void toolExecutes() {
        var tools = Tools.fromClass(new SampleTools());
        var greet = (FunctionToolImpl) tools.stream().filter(t -> t.name().equals("greet")).findFirst().orElseThrow();
        var result = greet.execute(new ToolArgs(Map.of("name", "Alice")), null);
        assertEquals("Hello, Alice!", ((ToolOutput.Text) result).content());
    }

    @Test
    void toolExecutesWithContext() {
        var tools = Tools.fromClass(new SampleTools());
        var tool = (FunctionToolImpl) tools.stream().filter(t -> t.name().equals("withContext")).findFirst().orElseThrow();
        var runCtx = new RunContext<>("ctx");
        var toolCtx = new ToolContext<>(runCtx, "call-42");
        var result = tool.execute(new ToolArgs(Map.of("input", "hi")), toolCtx);
        assertEquals("call:call-42 input:hi", ((ToolOutput.Text) result).content());
    }

    @Test
    void toolDefinitionHasParameterSchema() {
        var tools = Tools.fromClass(new SampleTools());
        var greet = (FunctionToolImpl) tools.stream().filter(t -> t.name().equals("greet")).findFirst().orElseThrow();
        var def = greet.definition();
        assertNotNull(def);
        assertTrue(def.parametersSchema().containsKey("properties"));
    }
}
