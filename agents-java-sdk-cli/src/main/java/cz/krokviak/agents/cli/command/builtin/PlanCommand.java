package cz.krokviak.agents.cli.command.builtin;

import cz.krokviak.agents.cli.CliContext;
import cz.krokviak.agents.cli.command.Command;
import cz.krokviak.agents.cli.plan.PlanStore;

public class PlanCommand implements Command {

    private final PlanStore planStore;

    public PlanCommand(PlanStore planStore) {
        this.planStore = planStore;
    }

    @Override public String name() { return "plan"; }
    @Override public String description() { return "Toggle plan mode (read-only exploration)"; }

    @Override
    public void execute(String args, CliContext ctx) {
        boolean entering = !ctx.isPlanMode();
        ctx.setPlanMode(entering);

        if (entering) {
            try {
                String slug = planStore.createPlan();
                String path = planStore.currentPlanPath();
                ctx.output().println("📋 Plan mode ON — plan: " + slug);
                ctx.output().println("  Plan file: " + path);
                ctx.output().println("  Only read-only tools available. /plan to exit.");
            } catch (Exception e) {
                ctx.output().printError("Failed to create plan file: " + e.getMessage());
            }
        } else {
            // Show plan content if exists
            String slug = planStore.currentSlug();
            if (slug != null) {
                try {
                    String content = planStore.loadPlan(slug);
                    if (content != null && !content.isBlank()) {
                        ctx.output().println("✓ Plan mode OFF — plan saved: " + slug);
                    } else {
                        ctx.output().println("✓ Plan mode OFF — all tools available");
                    }
                } catch (Exception e) {
                    ctx.output().println("✓ Plan mode OFF — all tools available");
                }
            } else {
                ctx.output().println("✓ Plan mode OFF — all tools available");
            }
        }
    }
}
