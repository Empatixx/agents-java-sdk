package cz.krokviak.agents.cli.hook.builtin;

import cz.krokviak.agents.cli.hook.*;
import cz.krokviak.agents.cli.tool.ToolClassifier;

/**
 * Blocks write tools when plan mode is active.
 * Read-only tools (read_file, glob, grep, etc.) are still allowed.
 */
public class PlanModeHook implements Hook {
    @Override public Phase phase() { return Phase.PRE_TOOL; }

    @Override
    public HookResult execute(ToolUseEvent event) {
        if (event.ctx().isPlanMode() && !ToolClassifier.isReadOnly(event.toolName())
                && !event.toolName().equals("enter_plan_mode")
                && !event.toolName().equals("exit_plan_mode")
                && !event.toolName().equals("ask_user")) {
            return new HookResult.Block("Plan mode active — only read-only tools allowed. Use exit_plan_mode to switch to implementation.");
        }
        return new HookResult.Proceed();
    }
}
