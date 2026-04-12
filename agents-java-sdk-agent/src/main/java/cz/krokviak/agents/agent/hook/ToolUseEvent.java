package cz.krokviak.agents.agent.hook;

import cz.krokviak.agents.api.AgentService;
import cz.krokviak.agents.tool.ToolOutput;
import java.util.Map;

/**
 * Payload for {@link cz.krokviak.agents.api.hook.Hook} invocations around tool
 * execution. Carries the active {@link AgentService} so hooks can query agent
 * state (plan mode, history, ...) without a frontend-specific context.
 */
public record ToolUseEvent(
    String toolName,
    Map<String, Object> args,
    AgentService agent,
    String toolCallId,
    ToolOutput result
) {
    public static ToolUseEvent preTool(String toolName, Map<String, Object> args,
                                       AgentService agent, String toolCallId) {
        return new ToolUseEvent(toolName, args, agent, toolCallId, null);
    }

    public static ToolUseEvent postTool(String toolName, Map<String, Object> args,
                                        AgentService agent, String toolCallId, ToolOutput result) {
        return new ToolUseEvent(toolName, args, agent, toolCallId, result);
    }
}
