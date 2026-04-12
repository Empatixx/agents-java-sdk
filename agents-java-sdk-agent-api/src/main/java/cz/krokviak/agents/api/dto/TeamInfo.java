package cz.krokviak.agents.api.dto;

import java.util.List;

/**
 * Snapshot of a named team — a group of related tasks / sub-agents that
 * share a {@code TaskManager}. Listed via
 * {@link cz.krokviak.agents.api.AgentService#listTeams()}.
 *
 * @param name     team identifier
 * @param taskIds  ids of tasks owned by the team (may be empty)
 */
public record TeamInfo(
    String name,
    List<String> taskIds
) {}
