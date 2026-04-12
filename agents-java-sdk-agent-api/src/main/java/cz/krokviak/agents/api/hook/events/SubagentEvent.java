package cz.krokviak.agents.api.hook.events;

/**
 * Dispatched when a sub-agent lifecycle transitions. SUBAGENT_START fires
 * with {@code status = "STARTING"} and {@code result = null}; SUBAGENT_STOP
 * fires with a terminal status (COMPLETED/FAILED/KILLED) and the final
 * result text (may be truncated for size).
 */
public record SubagentEvent(
    String agentId,
    String name,
    String status,
    String result,
    boolean background
) {}
