package cz.krokviak.agents.cli.plugin;

import cz.krokviak.agents.cli.CliContext;
import cz.krokviak.agents.cli.command.Command;
import cz.krokviak.agents.agent.hook.Hook;
import cz.krokviak.agents.cli.skill.Skill;

public interface PluginContext {
    void addCommand(Command command);
    void addHook(Hook hook);
    void addSkill(Skill skill);
    CliContext cliContext();
}
