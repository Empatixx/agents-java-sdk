package cz.krokviak.agents.cli.command.builtin;
import cz.krokviak.agents.cli.CliContext;
import cz.krokviak.agents.cli.command.Command;
import java.util.List;
public class PermissionsCommand implements Command {
    @Override public String name() { return "permissions"; }
    @Override public List<String> aliases() { return List.of("perms"); }
    @Override public String description() { return "Show permission rules"; }
    @Override public void execute(String args, CliContext ctx) {
        if (ctx.permissions() != null) { ctx.permissions().listRules(); }
        else { ctx.output().println("Permission system not active (trust mode)."); }
    }
}
