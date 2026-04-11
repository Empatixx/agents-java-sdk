package cz.krokviak.agents.cli.mcp;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class McpToolTest {

    @Test
    void qualifiedName() {
        var tool = new McpTool("github", "create_issue", "Create an issue", Map.of());
        assertEquals("mcp__github__create_issue", tool.qualifiedName());
    }

    @Test
    void qualifiedNameWithDashes() {
        var tool = new McpTool("my-server", "list-items", "List", Map.of());
        assertEquals("mcp__my-server__list-items", tool.qualifiedName());
    }

    @Test
    void fieldsPreserved() {
        var schema = Map.<String, Object>of("type", "object", "properties", Map.of(
            "title", Map.of("type", "string")));
        var tool = new McpTool("srv", "do_thing", "Does a thing", schema);

        assertEquals("srv", tool.serverName());
        assertEquals("do_thing", tool.toolName());
        assertEquals("Does a thing", tool.description());
        assertFalse(tool.inputSchema().isEmpty());
    }
}
