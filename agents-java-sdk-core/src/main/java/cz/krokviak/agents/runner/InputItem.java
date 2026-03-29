package cz.krokviak.agents.runner;

import java.util.List;
import java.util.Map;

public sealed interface InputItem permits InputItem.UserMessage, InputItem.AssistantMessage, InputItem.ToolResult, InputItem.SystemMessage {
    record UserMessage(String content) implements InputItem {}
    record AssistantMessage(String content, List<ToolCall> toolCalls) implements InputItem {
        public AssistantMessage(String content) { this(content, List.of()); }
    }
    record ToolResult(String toolCallId, String toolName, String output) implements InputItem {}
    record SystemMessage(String content) implements InputItem {}
    record ToolCall(String id, String name, Map<String, Object> arguments) {}
}
