package cz.krokviak.agents.api.dto;

/**
 * Snapshot of a running sub-agent. Returned by
 * {@link cz.krokviak.agents.api.AgentService#listRunningAgents()}.
 *
 * @param id          stable agent id (usually equals name)
 * @param name        human-readable label shown in status output
 * @param status      current lifecycle state ({@code STARTING}, {@code RUNNING},
 *                    {@code COMPLETED}, {@code FAILED}, {@code KILLED})
 * @param description short line describing what the agent is working on
 */
public record AgentInfo(
    String id,
    String name,
    String status,
    String description
) {}
