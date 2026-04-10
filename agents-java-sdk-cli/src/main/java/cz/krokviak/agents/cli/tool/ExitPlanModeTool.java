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

        // Load and display plan
        String slug = planStore.currentSlug();
        String planContent = null;
        if (slug != null) {
            try { planContent = planStore.loadPlan(slug); } catch (Exception ignored) {}
        }

        if (planContent != null && !planContent.isBlank()) {
            ctx.output().println("📋 Plan ready for review:");
            ctx.output().println("─".repeat(60));
            for (String line : planContent.split("\n")) {
                ctx.output().println("  " + line);
            }
            ctx.output().println("─".repeat(60));
            ctx.output().println("Type 'ok' to approve, or provide feedback to refine the plan.");
        } else {
            ctx.output().println("✓ Plan mode off — all tools available");
        }

        return ToolOutput.text("Plan mode deactivated. Plan shown to user for approval. " +
            "Wait for user response — they will approve or provide feedback.");
    }
}
