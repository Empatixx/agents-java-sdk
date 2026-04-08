package cz.krokviak.agents.cli.plugin;

import cz.krokviak.agents.cli.CliContext;
import cz.krokviak.agents.cli.command.Command;
import cz.krokviak.agents.cli.command.Commands;
import cz.krokviak.agents.cli.hook.Hook;
import cz.krokviak.agents.cli.hook.Hooks;

public class PluginContextImpl implements PluginContext {
    private final Commands commands;
    private final Hooks hooks;
    private final CliContext cliContext;

    public PluginContextImpl(Commands commands, Hooks hooks, CliContext cliContext) {
        this.commands = commands;
        this.hooks = hooks;
        this.cliContext = cliContext;
    }

    @Override public void addCommand(Command command) { commands.register(command); }
    @Override public void addHook(Hook hook) { hooks.register(hook); }
    @Override public CliContext cliContext() { return cliContext; }
}
