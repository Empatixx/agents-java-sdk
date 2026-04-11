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
        String slug = planStore.currentSlug();
        String planContent = null;
        if (slug != null) {
            try { planContent = planStore.loadPlan(slug); } catch (Exception ignored) {}
        }

        if (planContent == null || planContent.isBlank()) {
            ctx.setPlanMode(false);
            return ToolOutput.text("Plan mode off. No plan found.");
        }

        // Show plan in output
        ctx.output().println("");
        ctx.output().println("╭──── 📋 Plan: " + slug + " ────");
        for (String line : planContent.split("\n")) {
            ctx.output().println("│ " + line);
        }
        ctx.output().println("╰" + "─".repeat(50));

        // Use permission prompt UI for approval
        var renderer = ctx.promptRenderer();
        if (renderer != null) {
            {
                String[] options = {
                    "Approve — implement the plan",
                    "Reject — provide feedback to refine",
                    "Cancel — discard plan"
                };
                int selected = renderer.promptSelection("\ud83d\udccb " + slug, options);

                switch (selected) {
                    case 0 -> {
                        ctx.setPlanMode(false);
                        return ToolOutput.text("User APPROVED the plan. Implement it now.\n\nPlan:\n" + planContent);
                    }
                    case 1 -> {
                        // Stay in plan mode, user will type feedback
                        return ToolOutput.text("User REJECTED the plan. Stay in plan mode. " +
                            "Wait for user feedback — they will tell you what to change. " +
                            "Then update the plan and call exit_plan_mode again.");
                    }
                    default -> {
                        ctx.setPlanMode(false);
                        return ToolOutput.text("Plan cancelled by user.");
                    }
                }
            }
        }

        // Fallback: no TUI renderer
        ctx.setPlanMode(false);
        ctx.output().println("Type 'ok' to approve or provide feedback.");
        return ToolOutput.text("Plan shown. Wait for user response.");
    }

}
