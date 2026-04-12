package cz.krokviak.agents.api.dto;

import java.util.List;

/**
 * Parameters for {@link cz.krokviak.agents.api.AgentService#spawnAgent(SpawnRequest)}.
 *
 * @param agentName         label for the spawned agent; shown in status + task list
 * @param prompt            initial user-role instruction to the sub-agent
 * @param description       short human-readable purpose ("Investigate auth bug")
 * @param background        if {@code true}, spawn on a virtual thread and return its id immediately;
 *                          if {@code false}, run synchronously and return the final output
 * @param modelOverride     optional model id (overrides the parent agent's current model); {@code null} = inherit
 * @param maxTurns          ceiling on model↔tool round-trips; {@code null} = default (15)
 * @param toolNames         subset of the parent's tools exposed to the worker; empty = inherit all
 * @param isolatedWorktree  if {@code true}, run in a dedicated git worktree directory (read-write safe)
 */
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
