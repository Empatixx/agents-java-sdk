package cz.krokviak.agents.cli.tool;

import cz.krokviak.agents.cli.CliContext;
import cz.krokviak.agents.agent.plan.PlanStore;
import cz.krokviak.agents.context.ToolContext;
import cz.krokviak.agents.tool.*;

import java.util.List;
import java.util.Map;

public class EnterPlanModeTool implements ExecutableTool {
    private final CliContext ctx;
    private final PlanStore planStore;
    private final ToolDefinition toolDefinition;

    public EnterPlanModeTool(CliContext ctx, PlanStore planStore) {
        this.ctx = ctx;
        this.planStore = planStore;
        this.toolDefinition = new ToolDefinition("enter_plan_mode",
            "Enter plan mode. Only read-only tools available. Write your plan to the plan file. Call exit_plan_mode when done.",
            Map.of("type", "object", "properties", Map.of(
                "reason", Map.of("type", "string", "description", "Why you're entering plan mode")
            ), "required", List.of()));
    }

    @Override public String name() { return "enter_plan_mode"; }
    @Override public String description() { return toolDefinition.description(); }
    @Override public ToolDefinition definition() { return toolDefinition; }

    @Override
    public ToolOutput execute(ToolArgs args, ToolContext<?> toolCtx) {
        String reason = args.get("reason", String.class);
        ctx.agent().setPlanMode(true);

        String slug;
        try {
            slug = planStore.createPlan();
        } catch (Exception e) {
            return ToolOutput.text("Plan mode active but failed to create plan file: " + e.getMessage());
        }

        String path = planStore.currentPlanPath();
        ctx.output().println("📋 Plan mode ON" + (reason != null ? " — " + reason : ""));

        return ToolOutput.text(
            "Plan mode active. Only read-only tools available.\n" +
            "Plan file: " + path + "\n" +
            "Write your plan to this file using write_file or edit_file (these are allowed for the plan file).\n" +
            "Call exit_plan_mode when your plan is ready for user approval.");
    }
}
