package cz.krokviak.agents.cli.plugin;

import java.util.ServiceLoader;

public final class Plugins {
    private Plugins() {}

    public static void loadAll(PluginContext context) {
        ServiceLoader<CliPlugin> loader = ServiceLoader.load(CliPlugin.class);
        for (CliPlugin plugin : loader) {
            try {
                plugin.register(context);
                System.out.println("\033[2mLoaded plugin: " + plugin.name() + "\033[0m");
            } catch (Exception e) {
                System.err.println("Failed to load plugin " + plugin.name() + ": " + e.getMessage());
            }
        }
    }
}
