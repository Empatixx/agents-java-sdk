package cz.krokviak.agents.mcp;

import cz.krokviak.agents.context.ToolContext;
import cz.krokviak.agents.tool.ExecutableTool;
import cz.krokviak.agents.tool.ToolArgs;
import cz.krokviak.agents.tool.ToolDefinition;
import cz.krokviak.agents.tool.ToolOutput;

public final class MCPTool implements ExecutableTool {
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

    @Override
    public ToolDefinition definition() {
        return definition;
    }

    @Override
    public ToolOutput execute(ToolArgs args, ToolContext<?> ctx) throws Exception {
        return server.callTool(definition.name(), args.raw());
    }

    public MCPServer server() {
        return server;
    }
}
