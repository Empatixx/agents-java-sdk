package cz.krokviak.agents.agent.hook;

import cz.krokviak.agents.api.hook.HookPhase;

/**
 * CLI-side specialisation of {@link cz.krokviak.agents.api.hook.Hook}
 * bound to {@link ToolUseEvent} (carries a CliContext alongside the tool call).
 */
public interface Hook extends cz.krokviak.agents.api.hook.Hook<ToolUseEvent> {
    @Override
    HookPhase phase();
}
