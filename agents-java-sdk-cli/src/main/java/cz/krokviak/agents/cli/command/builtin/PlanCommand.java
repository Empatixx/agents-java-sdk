package cz.krokviak.agents.cli.command.builtin;

import cz.krokviak.agents.cli.CliContext;
import cz.krokviak.agents.cli.command.Command;

public class PlanCommand implements Command {
    @Override public String name() { return "plan"; }
    @Override public String description() { return "Toggle plan mode (read-only exploration)"; }
    @Override public void execute(String args, CliContext ctx) {
        boolean entering = !ctx.isPlanMode();
        ctx.setPlanMode(entering);
        if (entering) {
            ctx.output().println("\033[33m📋 Plan mode ON — only read-only tools available\033[0m");
        } else {
            ctx.output().println("\033[32m✓ Plan mode OFF — all tools available\033[0m");
        }
    }
}
