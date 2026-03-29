package cz.krokviak.agents.model;

import cz.krokviak.agents.runner.InputItem;
import cz.krokviak.agents.tool.ToolDefinition;
import java.util.List;

public record LlmContext(
    String systemPrompt,
    List<InputItem> messages,
    List<ToolDefinition> tools,
    Class<?> outputType,
    ModelSettings settings
) {}
