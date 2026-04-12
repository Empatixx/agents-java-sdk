package cz.krokviak.agents.cli.command.builtin;
import cz.krokviak.agents.cli.CliContext;
import cz.krokviak.agents.cli.command.Command;
public class SessionCommand implements Command {
    @Override public String name() { return "session"; }
    @Override public String description() { return "Show or switch session"; }
    @Override public void execute(String args, CliContext ctx) {
        if (args == null || args.isBlank()) {
            String id = ctx.agent().currentSessionId();
            ctx.output().println("Current session: " + (id != null ? id : "(none)"));
            return;
        }
        try {
            ctx.agent().loadSession(args.trim()).join();
            ctx.output().println("Switched to session: " + ctx.agent().currentSessionId() + " ("
                + ctx.agent().history().size() + " messages loaded)");
        } catch (Exception e) {
            ctx.output().printError("Failed to load session: " + e.getMessage());
        }
    }
}
