package cz.krokviak.agents.api.tool;

import java.util.Map;

/** UI-visible description of a registered tool. */
public record ToolDescriptor(
    String name,
    String description,
    Map<String, Object> inputSchema
) {}
