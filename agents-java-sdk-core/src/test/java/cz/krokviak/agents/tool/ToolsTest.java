package cz.krokviak.agents.tool;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ToolsTest {

    @Test
    void functionBuilderCreatesTool() {
        var tool = Tools.function("get_weather")
            .description("Get weather for a city")
            .param("city", String.class, "The city name")
            .handler((args, ctx) -> ToolOutput.text("Sunny in " + args.get("city", String.class)))
            .build();

        assertEquals("get_weather", tool.name());
        assertEquals("Get weather for a city", tool.description());
    }

    @Test
    void functionBuilderToolExecutes() {
        var tool = Tools.function("echo")
            .description("Echo input")
            .param("text", String.class, "The text to echo")
            .handler((args, ctx) -> ToolOutput.text(args.get("text", String.class)))
            .build();

        var result = tool.execute(new ToolArgs(Map.of("text", "hello")), null);
        assertEquals("hello", ((ToolOutput.Text) result).content());
    }

    @Test
    void functionBuilderToolHasDefinition() {
        var tool = Tools.function("calc")
            .description("Calculate")
            .param("a", int.class, "first number")
            .param("b", int.class, "second number")
            .handler((args, ctx) -> ToolOutput.text("result"))
            .build();

        var def = tool.definition();
        assertEquals("calc", def.name());
        @SuppressWarnings("unchecked")
        var props = (Map<String, Object>) def.parametersSchema().get("properties");
        assertTrue(props.containsKey("a"));
        assertTrue(props.containsKey("b"));
    }

    @Test
    void toolIsSealedInterface() {
        Tool tool = Tools.function("t").description("d").handler((a, c) -> ToolOutput.text("")).build();
        assertInstanceOf(Tool.class, tool);
        assertInstanceOf(FunctionToolImpl.class, tool);
    }
}
