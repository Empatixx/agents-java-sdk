package cz.krokviak.agents.api.dto;

/**
 * Aggregate token + cost counters for the current session. Returned by
 * {@link cz.krokviak.agents.api.AgentService#costs()}.
 *
 * @param inputTokens   total input (prompt) tokens billed so far
 * @param outputTokens  total output (completion) tokens billed so far
 * @param totalUsd      monetary total in USD, estimated from the model's pricing table
 * @param formatted     pre-rendered human-readable line (e.g. {@code "[tokens: 12,345 in, 4,567 out | cost: $0.1234]"})
 */
public record CostSummary(
    long inputTokens,
    long outputTokens,
    double totalUsd,
    String formatted
) {}
