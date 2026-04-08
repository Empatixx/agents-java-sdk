package cz.krokviak.agents.cli.command.builtin;
import cz.krokviak.agents.cli.CliContext;
import cz.krokviak.agents.cli.command.Command;
public class ClearCommand implements Command {
    @Override public String name() { return "clear"; }
    @Override public String description() { return "Clear conversation history"; }
    @Override public void execute(String args, CliContext ctx) {
        ctx.history().clear();
        ctx.output().println("Conversation history cleared.");
    }
}
