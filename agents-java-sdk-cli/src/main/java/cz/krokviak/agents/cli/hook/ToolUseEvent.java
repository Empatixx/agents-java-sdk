package cz.krokviak.agents.cli.hook;

import cz.krokviak.agents.cli.CliContext;
import cz.krokviak.agents.tool.ToolOutput;
import java.util.Map;

public record ToolUseEvent(
    String toolName,
    Map<String, Object> args,
    CliContext ctx,
    String toolCallId,
    ToolOutput result
) {
    public static ToolUseEvent preTool(String toolName, Map<String, Object> args,
                                       CliContext ctx, String toolCallId) {
        return new ToolUseEvent(toolName, args, ctx, toolCallId, null);
    }

    public static ToolUseEvent postTool(String toolName, Map<String, Object> args,
                                        CliContext ctx, String toolCallId, ToolOutput result) {
        return new ToolUseEvent(toolName, args, ctx, toolCallId, result);
    }
}
