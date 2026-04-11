package cz.krokviak.agents.cli.command.builtin;

import cz.krokviak.agents.cli.CliContext;
import cz.krokviak.agents.cli.command.Command;
import cz.krokviak.agents.cli.plugin.LoadedPlugin;
import cz.krokviak.agents.cli.plugin.Plugins;

public class PluginsCommand implements Command {
    @Override public String name() { return "plugins"; }
    @Override public String description() { return "List loaded plugins"; }

    @Override
    public void execute(String args, CliContext ctx) {
        var plugins = Plugins.loaded();
        if (plugins.isEmpty()) {
            ctx.output().println("No plugins loaded.");
            ctx.output().println("Place plugins in ~/.krok/plugins/ or .krok/plugins/");
            ctx.output().println("Each plugin needs a plugin.json manifest.");
            return;
        }

        ctx.output().println("Loaded plugins:");
        for (LoadedPlugin p : plugins) {
            ctx.output().println("  " + p.name()
                + (p.description().isBlank() ? "" : " — " + p.description()));
            ctx.output().println("    " + p.rootPath());
            if (!p.commands().isEmpty())
                ctx.output().println("    Commands: " + p.commands().stream()
                    .map(c -> "/" + c.name()).toList());
            if (!p.skills().isEmpty())
                ctx.output().println("    Skills: " + p.skills().stream()
                    .map(s -> s.name()).toList());
            if (!p.hooks().isEmpty())
                ctx.output().println("    Hooks: " + p.hooks().size());
        }
    }
}
