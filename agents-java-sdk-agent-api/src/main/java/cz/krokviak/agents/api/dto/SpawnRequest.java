package cz.krokviak.agents.api.dto;

import java.util.List;

public record SpawnRequest(
    String agentName,
    String prompt,
    String description,
    boolean background,
    String modelOverride,
    Integer maxTurns,
    List<String> toolNames,
    boolean isolatedWorktree
) {}
