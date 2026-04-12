package cz.krokviak.agents.api.dto;

public record AgentInfo(
    String id,
    String name,
    String status,
    String description
) {}
