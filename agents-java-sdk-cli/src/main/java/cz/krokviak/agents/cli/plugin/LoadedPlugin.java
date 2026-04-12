package cz.krokviak.agents.cli.plugin;

import cz.krokviak.agents.cli.command.Command;
import cz.krokviak.agents.agent.hook.Hook;
import cz.krokviak.agents.cli.skill.Skill;

import java.nio.file.Path;
import java.util.List;

/**
 * A fully loaded plugin with all its components resolved.
 */
public record LoadedPlugin(
    PluginManifest manifest,
    Path rootPath,
    List<Command> commands,
    List<Skill> skills,
    List<Hook> hooks
) {
    public String name() { return manifest.name(); }
    public String description() { return manifest.description() != null ? manifest.description() : ""; }
}
