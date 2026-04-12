package cz.krokviak.agents.api.dto;

public record PermissionRule(
    String pattern,
    String mode
) {}
