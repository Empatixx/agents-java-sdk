package cz.krokviak.agents.cli.event;

/**
 * @deprecated Use {@link cz.krokviak.agents.agent.event.DefaultEventBus} directly.
 * Kept only so legacy imports `new CliEventBus()` keep working during Phase 2.
 */
@Deprecated(forRemoval = true)
public final class CliEventBus extends cz.krokviak.agents.agent.event.DefaultEventBus {
}
