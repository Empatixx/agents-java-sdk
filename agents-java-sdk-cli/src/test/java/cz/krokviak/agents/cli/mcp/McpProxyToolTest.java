package cz.krokviak.agents.cli.mcp;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class McpProxyToolTest {

    @Test
    void nameIsQualified() {
        var mcpTool = new McpTool("github", "create_issue", "Create issue",
            Map.of("type", "object"));
        var proxy = new McpProxyTool(new McpManager(), mcpTool);

        assertEquals("mcp__github__create_issue", proxy.name());
    }

    @Test
    void descriptionFromMcpTool() {
        var mcpTool = new McpTool("srv", "tool", "My description", Map.of());
        var proxy = new McpProxyTool(new McpManager(), mcpTool);

        assertEquals("My description", proxy.description());
    }

    @Test
    void definitionHasSchema() {
        var schema = Map.<String, Object>of("type", "object", "properties",
            Map.of("title", Map.of("type", "string")));
        var mcpTool = new McpTool("srv", "tool", "desc", schema);
        var proxy = new McpProxyTool(new McpManager(), mcpTool);

        assertNotNull(proxy.definition());
        assertEquals("mcp__srv__tool", proxy.definition().name());
        assertFalse(proxy.definition().parametersSchema().isEmpty());
    }

    @Test
    void executeWithNoServerReturnsError() throws Exception {
        var mcpTool = new McpTool("missing", "tool", "desc", Map.of());
        var proxy = new McpProxyTool(new McpManager(), mcpTool);

        var result = proxy.execute(
            new cz.krokviak.agents.tool.ToolArgs(Map.of("key", "value")), null);

        assertTrue(result instanceof cz.krokviak.agents.tool.ToolOutput.Text t
            && t.content().startsWith("Error:"));
    }
}
