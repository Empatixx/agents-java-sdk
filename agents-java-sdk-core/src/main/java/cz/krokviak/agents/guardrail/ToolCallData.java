package cz.krokviak.agents.guardrail;

import java.util.Map;

public record ToolCallData(String toolName, Map<String, Object> arguments) {}
