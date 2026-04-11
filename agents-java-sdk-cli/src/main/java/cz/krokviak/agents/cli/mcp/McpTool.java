package cz.krokviak.agents.cli.mcp;

import java.util.Map;

/**
 * A tool exposed by an MCP server (from tools/list response).
 */
public record McpTool(
    String serverName,
    String toolName,
    String description,
    Map<String, Object> inputSchema
) {
    /**
     * Qualified tool name: mcp__servername__toolname
     */
    public String qualifiedName() {
        return "mcp__" + serverName + "__" + toolName;
    }
}
