package cz.krokviak.agents.api.dto;

public record RunTurnResult(
    String finalOutput,
    int turns,
    long inputTokens,
    long outputTokens,
    boolean interrupted
) {}
