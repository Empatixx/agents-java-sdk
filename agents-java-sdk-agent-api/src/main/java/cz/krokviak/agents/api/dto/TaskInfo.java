package cz.krokviak.agents.api.dto;

public record TaskInfo(
    String id,
    String description,
    String status,
    String summary,
    long startedAt,
    long finishedAt
) {}
