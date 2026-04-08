package cz.krokviak.agents.cli.command.builtin;
import cz.krokviak.agents.cli.CliContext;
import cz.krokviak.agents.cli.command.Command;
import cz.krokviak.agents.cli.command.Commands;
import java.util.List;
public class HelpCommand implements Command {
    private final Commands commands;
    public HelpCommand(Commands commands) { this.commands = commands; }
    @Override public String name() { return "help"; }
    @Override public List<String> aliases() { return List.of("?"); }
    @Override public String description() { return "Show available commands"; }
    @Override public void execute(String args, CliContext ctx) {
        ctx.output().println("Available commands:");
        for (Command cmd : commands.all()) {
            String aliases = cmd.aliases().isEmpty() ? "" : " (aliases: " + String.join(", ", cmd.aliases()) + ")";
            ctx.output().println("  /" + cmd.name() + aliases + " — " + cmd.description());
        }
    }
}
