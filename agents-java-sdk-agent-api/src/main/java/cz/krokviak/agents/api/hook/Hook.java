package cz.krokviak.agents.api.hook;

/**
 * Extension point invoked around tool/model execution. Parameterised by
 * the event type so concrete frontends can carry their own context
 * (e.g. CLI passes a CliContext via its ToolUseEvent).
 */
public interface Hook<E> {
    HookPhase phase();
    HookResult execute(E event);
}
