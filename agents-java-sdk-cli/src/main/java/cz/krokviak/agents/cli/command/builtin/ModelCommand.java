package cz.krokviak.agents.cli.command.builtin;
import cz.krokviak.agents.cli.CliContext;
import cz.krokviak.agents.cli.command.Command;
public class ModelCommand implements Command {
    @Override public String name() { return "model"; }
    @Override public String description() { return "Show or switch model"; }
    @Override public void execute(String args, CliContext ctx) {
        if (args == null || args.isBlank()) {
            ctx.output().println("Current model: " + ctx.modelId());
        } else {
            ctx.switchModel(args.trim());
            ctx.output().println("Switched to model: " + ctx.modelId());
        }
    }
}
