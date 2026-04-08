package cz.krokviak.agents.cli.command.builtin;
import cz.krokviak.agents.cli.CliContext;
import cz.krokviak.agents.cli.command.Command;
import java.util.List;
public class CostCommand implements Command {
    @Override public String name() { return "cost"; }
    @Override public List<String> aliases() { return List.of("usage"); }
    @Override public String description() { return "Show token usage and cost"; }
    @Override public void execute(String args, CliContext ctx) {
        ctx.output().printUsage(ctx.costTracker().format());
    }
}
