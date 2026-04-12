package cz.krokviak.agents.cli.plugin;

import cz.krokviak.agents.cli.CliContext;
import cz.krokviak.agents.cli.command.Command;
import cz.krokviak.agents.cli.command.Commands;
import cz.krokviak.agents.agent.hook.Hook;
import cz.krokviak.agents.agent.hook.Hooks;
import cz.krokviak.agents.cli.skill.Skill;
import cz.krokviak.agents.cli.skill.SkillRegistry;

public class PluginContextImpl implements PluginContext {
    private final Commands commands;
    private final Hooks hooks;
    private final SkillRegistry skillRegistry;
    private final CliContext cliContext;

    public PluginContextImpl(Commands commands, Hooks hooks, SkillRegistry skillRegistry, CliContext cliContext) {
        this.commands = commands;
        this.hooks = hooks;
        this.skillRegistry = skillRegistry;
        this.cliContext = cliContext;
    }

    @Override public void addCommand(Command command) { commands.register(command); }
    @Override public void addHook(Hook hook) { hooks.register(hook); }
    @Override public void addSkill(Skill skill) { skillRegistry.register(skill); }
    @Override public CliContext cliContext() { return cliContext; }
}
