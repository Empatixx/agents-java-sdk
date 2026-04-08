package cz.krokviak.agents.cli.plugin;

import cz.krokviak.agents.cli.CliContext;
import cz.krokviak.agents.cli.command.Command;
import cz.krokviak.agents.cli.hook.Hook;

public interface PluginContext {
    void addCommand(Command command);
    void addHook(Hook hook);
    CliContext cliContext();
}
