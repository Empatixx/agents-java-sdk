package cz.krokviak.agents.tool;

import cz.krokviak.agents.context.ToolContext;

public interface ExecutableTool extends Tool {
    ToolOutput execute(ToolArgs args, ToolContext<?> ctx) throws Exception;
    ToolDefinition definition();
}
