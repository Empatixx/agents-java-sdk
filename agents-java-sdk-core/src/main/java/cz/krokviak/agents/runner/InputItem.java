package cz.krokviak.agents.runner;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public sealed interface InputItem permits InputItem.UserMessage, InputItem.AssistantMessage, InputItem.ToolResult, InputItem.SystemMessage, InputItem.CompactionMarker, InputItem.ImageContent {
    record UserMessage(String content) implements InputItem {}
    /** Image content for vision — sent as base64 to the API. */
    record ImageContent(String filePath, String mediaType, String base64Data, String description) implements InputItem {
        public ImageContent(String filePath, String mediaType, String base64Data) {
            this(filePath, mediaType, base64Data, null);
        }
    }
    record AssistantMessage(String content, List<ToolCall> toolCalls) implements InputItem {
        public AssistantMessage(String content) { this(content, List.of()); }
    }
    record ToolResult(String toolCallId, String toolName, String output) implements InputItem {}
    record SystemMessage(String content) implements InputItem {}
    record CompactionMarker(String summary, Instant compactedAt, int messagesCompacted) implements InputItem {}
    record ToolCall(String id, String name, Map<String, Object> arguments) {}
}
