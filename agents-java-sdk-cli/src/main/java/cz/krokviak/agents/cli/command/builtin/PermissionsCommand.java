package cz.krokviak.agents.cli.command.builtin;
import cz.krokviak.agents.cli.CliContext;
import cz.krokviak.agents.cli.command.Command;
import java.util.List;
public class PermissionsCommand implements Command {
    @Override public String name() { return "permissions"; }
    @Override public List<String> aliases() { return List.of("perms"); }
    @Override public String description() { return "Show permission rules"; }
    @Override public void execute(String args, CliContext ctx) {
        var rules = ctx.agent().permissionRules();
        if (rules.isEmpty()) { ctx.output().println("No session permission rules."); return; }
        ctx.output().println("Session permission rules:");
        for (var r : rules) {
            ctx.output().println("  " + r.mode() + " " + r.pattern());
        }
    }
}
