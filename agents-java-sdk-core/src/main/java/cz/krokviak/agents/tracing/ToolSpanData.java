package cz.krokviak.agents.tracing;

import java.util.Map;

public record ToolSpanData(
    String toolName,
    String toolType,
    Map<String, Object> arguments
) {
    public ToolSpanData(String toolName) {
        this(toolName, "function", Map.of());
    }
}
