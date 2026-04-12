package cz.krokviak.agents.api.dto;

import java.time.Instant;

public record SessionInfo(
    String sessionId,
    String modelId,
    String title,
    Instant createdAt,
    Instant lastActivityAt,
    int messageCount,
    String preview
) {}
