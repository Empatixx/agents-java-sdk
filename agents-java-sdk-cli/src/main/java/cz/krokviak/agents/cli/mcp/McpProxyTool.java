package cz.krokviak.agents.cli.mcp;

import cz.krokviak.agents.context.ToolContext;
import cz.krokviak.agents.tool.*;

import java.util.Map;

/**
 * Wraps a single MCP tool as an ExecutableTool for the CLI tool system.
 * Tool name: mcp__servername__toolname
 */
public class McpProxyTool implements ExecutableTool {

    private final McpManager mcpManager;
    private final McpTool mcpTool;
    private final ToolDefinition toolDefinition;

    public McpProxyTool(McpManager mcpManager, McpTool mcpTool) {
        this.mcpManager = mcpManager;
        this.mcpTool = mcpTool;
        this.toolDefinition = new ToolDefinition(
            mcpTool.qualifiedName(),
            mcpTool.description(),
            mcpTool.inputSchema().isEmpty()
                ? Map.of("type", "object", "properties", Map.of())
                : mcpTool.inputSchema()
        );
    }

    @Override public String name() { return mcpTool.qualifiedName(); }
    @Override public String description() { return mcpTool.description(); }
    @Override public ToolDefinition definition() { return toolDefinition; }

    @Override
    public ToolOutput execute(ToolArgs args, ToolContext<?> ctx) {
        String result = mcpManager.callTool(mcpTool.serverName(), mcpTool.toolName(), args.raw());
        return ToolOutput.text(result);
    }
}
