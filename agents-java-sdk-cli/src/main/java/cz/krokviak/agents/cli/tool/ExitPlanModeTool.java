package cz.krokviak.agents.cli.tool;

import cz.krokviak.agents.cli.CliContext;
import cz.krokviak.agents.agent.plan.PlanStore;
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
            ctx.agent().setPlanMode(false);
            return ToolOutput.text("Plan mode off. No plan found.");
        }

        // Show plan in output
        ctx.output().println("");
        ctx.output().println("╭──── 📋 Plan: " + slug + " ────");
        for (String line : planContent.split("\n")) {
            ctx.output().println("│ " + line);
        }
        ctx.output().println("╰" + "─".repeat(50));

        // Route approval through the UI-agnostic question flow.
        int selected;
        try {
            selected = ctx.agent().requestQuestion("\ud83d\udccb " + slug, java.util.List.of(
                "Approve — implement the plan",
                "Reject — provide feedback to refine",
                "Cancel — discard plan"
            )).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            ctx.agent().setPlanMode(false);
            return ToolOutput.text("Plan approval interrupted.");
        } catch (java.util.concurrent.ExecutionException e) {
            ctx.agent().setPlanMode(false);
            return ToolOutput.text("Plan approval failed: " + e.getMessage());
        }

        return switch (selected) {
            case 0 -> {
                ctx.agent().setPlanMode(false);
                yield ToolOutput.text("User APPROVED the plan. Implement it now.\n\nPlan:\n" + planContent);
            }
            case 1 -> ToolOutput.text("User REJECTED the plan. Stay in plan mode. " +
                "Wait for user feedback — they will tell you what to change. " +
                "Then update the plan and call exit_plan_mode again.");
            default -> {
                ctx.agent().setPlanMode(false);
                yield ToolOutput.text("Plan cancelled by user.");
            }
        };
    }

}
