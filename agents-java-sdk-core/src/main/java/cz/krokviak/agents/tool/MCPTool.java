package cz.krokviak.agents.tool;

import cz.krokviak.agents.mcp.MCPServer;

import java.util.Map;

/**
 * Bridges an MCP server tool into the Agent SDK's Tool sealed interface.
 * Delegates execution to the backing MCPServer.
 */
public final class MCPTool implements Tool {
    private final ToolDefinition definition;
    private final MCPServer server;

    public MCPTool(ToolDefinition definition, MCPServer server) {
        this.definition = definition;
        this.server = server;
    }

    @Override
    public String name() {
        return definition.name();
    }

    @Override
    public String description() {
        return definition.description();
    }

    public ToolDefinition definition() {
        return definition;
    }

    /**
     * Execute this tool by delegating to the MCP server.
     */
    public ToolOutput execute(Map<String, Object> arguments) throws Exception {
        return server.callTool(definition.name(), arguments);
    }

    public MCPServer server() {
        return server;
    }
}
