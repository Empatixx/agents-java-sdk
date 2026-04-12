package cz.krokviak.agents.agent.hook.builtin;

import cz.krokviak.agents.api.hook.HookPhase;
import cz.krokviak.agents.api.hook.HookResult;

import cz.krokviak.agents.agent.guardrail.BuiltinGuardrails;
import cz.krokviak.agents.agent.hook.*;

public class GuardrailHook implements Hook {
    @Override public HookPhase phase() { return HookPhase.PRE_TOOL; }

    @Override
    public HookResult execute(ToolUseEvent event) {
        if (!BuiltinGuardrails.checkToolArgSafety(event.toolName(), event.args())) {
            return new HookResult.Block("Guardrail: dangerous operation blocked (sensitive file or dangerous command pattern).");
        }
        return new HookResult.Proceed();
    }
}
