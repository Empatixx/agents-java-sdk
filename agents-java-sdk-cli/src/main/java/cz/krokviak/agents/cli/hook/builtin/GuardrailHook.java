package cz.krokviak.agents.cli.hook.builtin;

import cz.krokviak.agents.cli.guardrail.BuiltinGuardrails;
import cz.krokviak.agents.cli.hook.*;

public class GuardrailHook implements Hook {
    @Override public Phase phase() { return Phase.PRE_TOOL; }

    @Override
    public HookResult execute(ToolUseEvent event) {
        if (!BuiltinGuardrails.checkToolArgSafety(event.toolName(), event.args())) {
            return new HookResult.Block("Guardrail: dangerous operation blocked (sensitive file or dangerous command pattern).");
        }
        return new HookResult.Proceed();
    }
}
