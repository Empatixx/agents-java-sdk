package cz.krokviak.agents.cli.plugin;

public interface CliPlugin {
    String name();
    default String description() { return ""; }
    void register(PluginContext context);
}
