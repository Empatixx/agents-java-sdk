package cz.krokviak.agents.cli.command.builtin;
import cz.krokviak.agents.cli.CliContext;
import cz.krokviak.agents.cli.command.Command;
import java.util.List;
public class ExitCommand implements Command {
    @Override public String name() { return "exit"; }
    @Override public List<String> aliases() { return List.of("quit"); }
    @Override public String description() { return "Exit the CLI"; }
    @Override public void execute(String args, CliContext ctx) {
        ctx.output().println("Goodbye!");
        ctx.output().printUsage(ctx.costTracker().format());
        System.exit(0);
    }
}
