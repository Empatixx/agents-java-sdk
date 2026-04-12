package cz.krokviak.agents.cli.event;

import cz.krokviak.agents.api.event.AgentEvent;

/**
 * Deprecated marker — use {@link AgentEvent} directly.
 * Kept as a transitional alias so legacy call sites declaring
 * {@code CliEvent} as a type still compile while migration is in progress.
 */
@Deprecated(forRemoval = true)
public interface CliEvent extends AgentEvent {
}
