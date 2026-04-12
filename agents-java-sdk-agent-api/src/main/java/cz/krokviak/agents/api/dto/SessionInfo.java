package cz.krokviak.agents.api.dto;

import java.time.Instant;

/**
 * Metadata of a persisted session. Listed via
 * {@link cz.krokviak.agents.api.AgentService#listSessions()}.
 *
 * @param sessionId       stable opaque id (e.g. UUID string)
 * @param modelId         model that was active when the session ended; may be {@code null} for legacy entries
 * @param title           short human-readable label (derived from first user message)
 * @param createdAt       session creation timestamp
 * @param lastActivityAt  last turn timestamp — used by {@code /resume} to sort sessions
 * @param messageCount    total messages (user + assistant + tool) stored
 * @param preview         first ~200 chars of the conversation, useful for picker UIs
 */
public record SessionInfo(
    String sessionId,
    String modelId,
    String title,
    Instant createdAt,
    Instant lastActivityAt,
    int messageCount,
    String preview
) {}
