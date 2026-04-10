package cz.krokviak.agents.cli.tool;

import cz.krokviak.agents.cli.CliContext;
import cz.krokviak.agents.cli.plan.PlanStore;
import cz.krokviak.agents.context.ToolContext;
import cz.krokviak.agents.tool.*;

import java.util.List;
import java.util.Map;

public class ExitPlanModeTool implements ExecutableTool {
    private final CliContext ctx;
    private final PlanStore planStore;
    private final ToolDefinition toolDefinition;

    public ExitPlanModeTool(CliContext ctx, PlanStore planStore) {
        this.ctx = ctx;
        this.planStore = planStore;
        this.toolDefinition = new ToolDefinition("exit_plan_mode",
            "Exit plan mode. The plan will be shown to the user for approval.",
            Map.of("type", "object", "properties", Map.of(), "required", List.of()));
    }

    @Override public String name() { return "exit_plan_mode"; }
    @Override public String description() { return toolDefinition.description(); }
    @Override public ToolDefinition definition() { return toolDefinition; }

    @Override
    public ToolOutput execute(ToolArgs args, ToolContext<?> toolCtx) {
        ctx.setPlanMode(false);

        String slug = planStore.currentSlug();
        String planContent = null;
        if (slug != null) {
            try { planContent = planStore.loadPlan(slug); } catch (Exception ignored) {}
        }

        if (planContent != null && !planContent.isBlank()) {
            ctx.output().println("");
            ctx.output().println("╭──── 📋 Plan: " + slug + " ────");
            for (String line : planContent.split("\n")) {
                ctx.output().println("│ " + line);
            }
            ctx.output().println("╰" + "─".repeat(50));
            ctx.output().println("");
            ctx.output().println("Type 'ok' to approve and implement, or provide feedback to refine.");

            return ToolOutput.text(
                "Plan shown to user. STOP and wait for user response.\n" +
                "- If user says 'ok' or 'approve': implement the plan.\n" +
                "- If user provides feedback: re-enter plan mode and update the plan.\n" +
                "DO NOT proceed with implementation until user approves.");
        } else {
            ctx.output().println("✓ Plan mode off — all tools available");
            return ToolOutput.text("Plan mode deactivated. No plan file found.");
        }
    }
}
