package cz.krokviak.agents.runner;

import cz.krokviak.agents.tool.ToolOutput;
import java.util.Map;

public sealed interface RunItem permits RunItem.MessageOutput, RunItem.ToolCallItem, RunItem.ToolOutputItem, RunItem.HandoffItem {
    record MessageOutput(String agentName, String content) implements RunItem {}
    record ToolCallItem(String agentName, String toolCallId, String toolName, Map<String, Object> arguments) implements RunItem {}
    record ToolOutputItem(String agentName, String toolCallId, String toolName, ToolOutput output) implements RunItem {}
    record HandoffItem(String fromAgent, String toAgent) implements RunItem {}
}
