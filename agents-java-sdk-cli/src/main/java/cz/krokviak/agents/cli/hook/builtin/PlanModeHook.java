package cz.krokviak.agents.cli.hook.builtin;

import cz.krokviak.agents.api.hook.HookPhase;
import cz.krokviak.agents.api.hook.HookResult;

import cz.krokviak.agents.cli.hook.*;
import cz.krokviak.agents.agent.plan.PlanStore;
import cz.krokviak.agents.cli.tool.ToolClassifier;

import java.util.Set;

/**
 * Blocks write tools when plan mode is active.
 * Exception: write_file/edit_file are allowed for the plan file itself.
 */
public class PlanModeHook implements Hook {

    private static final Set<String> ALWAYS_ALLOWED = Set.of(
        "enter_plan_mode", "exit_plan_mode", "ask_user"
    );

    private final PlanStore planStore;

    public PlanModeHook(PlanStore planStore) {
        this.planStore = planStore;
    }

    @Override public HookPhase phase() { return HookPhase.PRE_TOOL; }

    @Override
    public HookResult execute(ToolUseEvent event) {
        if (!event.ctx().isPlanMode()) return new HookResult.Proceed();
        if (ToolClassifier.isReadOnly(event.toolName())) return new HookResult.Proceed();
        if (ALWAYS_ALLOWED.contains(event.toolName())) return new HookResult.Proceed();

        // Allow write_file/edit_file if targeting the plan file
        if (("write_file".equals(event.toolName()) || "edit_file".equals(event.toolName()))
                && isPlanFile(event)) {
            return new HookResult.Proceed();
        }

        return new HookResult.Block("Plan mode active — only read-only tools allowed. " +
            "You can write to the plan file at: " + planStore.currentPlanPath() + ". " +
            "Use exit_plan_mode when done.");
    }

    private boolean isPlanFile(ToolUseEvent event) {
        String planPath = planStore.currentPlanPath();
        if (planPath == null) return false;
        Object filePath = event.args().get("file_path");
        return filePath != null && planPath.equals(filePath.toString());
    }
}
