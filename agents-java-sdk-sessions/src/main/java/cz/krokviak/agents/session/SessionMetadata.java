package cz.krokviak.agents.session;

import java.time.Instant;

public record SessionMetadata(
    String sessionId,
    String title,
    Instant createdAt,
    Instant lastActivityAt,
    int messageCount,
    String workingDirectory
) {}
