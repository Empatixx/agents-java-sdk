package cz.krokviak.agents.cli.command.builtin;
import cz.krokviak.agents.cli.CliContext;
import cz.krokviak.agents.cli.command.Command;
public class SessionCommand implements Command {
    @Override public String name() { return "session"; }
    @Override public String description() { return "Show or switch session"; }
    @Override public void execute(String args, CliContext ctx) {
        if (args == null || args.isBlank()) {
            ctx.output().println("Current session: " + (ctx.sessionId() != null ? ctx.sessionId() : "(none)"));
            return;
        }
        if (ctx.session() == null) { ctx.output().printError("No session backend configured."); return; }
        ctx.setSessionId(args.trim());
        var loaded = ctx.session().getHistory(ctx.sessionId());
        ctx.history().clear();
        ctx.history().addAll(loaded);
        ctx.output().println("Switched to session: " + ctx.sessionId() + " (" + loaded.size() + " messages loaded)");
    }
}
