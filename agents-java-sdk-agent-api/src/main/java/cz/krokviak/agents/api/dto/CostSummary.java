package cz.krokviak.agents.api.dto;

public record CostSummary(
    long inputTokens,
    long outputTokens,
    double totalUsd,
    String formatted
) {}
