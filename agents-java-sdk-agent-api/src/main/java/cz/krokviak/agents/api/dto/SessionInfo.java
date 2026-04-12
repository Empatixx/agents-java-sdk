package cz.krokviak.agents.api.dto;

public record SessionInfo(
    String sessionId,
    String modelId,
    long createdAt,
    long updatedAt,
    int messageCount,
    String preview
) {}
