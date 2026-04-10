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
            try { planStore.createPlan(); } catch (Exception ignored) {}
        }
        // No output — info panel shows plan mode status
    }
}
