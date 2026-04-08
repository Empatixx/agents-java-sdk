package cz.krokviak.agents.cli.command.builtin;

import cz.krokviak.agents.cli.CliContext;
import cz.krokviak.agents.cli.command.Command;
import cz.krokviak.agents.cli.hook.Hooks;

import java.util.List;

public class HooksCommand implements Command {
    private final Hooks hooks;
    public HooksCommand(Hooks hooks) { this.hooks = hooks; }

    @Override public String name() { return "hooks"; }
    @Override public List<String> aliases() { return List.of("hook"); }
    @Override public String description() { return "Show registered hooks"; }
    @Override public void execute(String args, CliContext ctx) {
        ctx.output().println("Registered hooks:");
        hooks.describe().forEach(line -> ctx.output().println("  " + line));
    }
}
