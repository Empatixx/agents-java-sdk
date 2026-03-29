package cz.krokviak.agents.model;

import java.util.List;

public record ModelResponse(
    String id,
    List<OutputItem> output,
    Usage usage
) {
    public sealed interface OutputItem permits OutputItem.Message, OutputItem.ToolCallRequest {
        record Message(String content) implements OutputItem {}
        record ToolCallRequest(
            String id,
            String name,
            java.util.Map<String, Object> arguments
        ) implements OutputItem {}
    }
}
