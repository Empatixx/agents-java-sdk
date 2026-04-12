package cz.krokviak.agents.api.hook;

/**
 * Extension points the agent fires during a run. Plugins register
 * {@link Hook}s against one of these phases; the engine dispatches the
 * matching payload (see {@code cz.krokviak.agents.api.hook.events}) at
 * the right moment in the lifecycle.
 */
public enum HookPhase {
    /** Before a tool call executes. Payload: {@code ToolUseEvent}. */
    PRE_TOOL,
    /** After a tool call executes. Payload: {@code ToolUseEvent}. */
    POST_TOOL,
    /** Before the model is called. Reserved; not currently dispatched. */
    PRE_MODEL,
    /** After the model returns. Reserved; not currently dispatched. */
    POST_MODEL,
    /** Once per {@code runTurn()}, before the model sees the input. Payload: {@code PreTurnEvent}. */
    PRE_TURN,
    /** Once per {@code runTurn()}, after the final turn of the loop. Payload: {@code PostTurnEvent}. A {@code Block} stops continuation. */
    POST_TURN,
    /** Before history compaction. Payload: {@code CompactEvent}. */
    PRE_COMPACT,
    /** After history compaction. Payload: {@code CompactEvent}. */
    POST_COMPACT,
    /** A sub-agent is about to start. Payload: {@code SubagentEvent}. */
    SUBAGENT_START,
    /** A sub-agent finished (completed / failed / killed). Payload: {@code SubagentEvent}. */
    SUBAGENT_STOP,
    /** Session opened. Payload: {@code SessionEvent}. */
    SESSION_START,
    /** Session closed. Payload: {@code SessionEvent}. */
    SESSION_END
}
