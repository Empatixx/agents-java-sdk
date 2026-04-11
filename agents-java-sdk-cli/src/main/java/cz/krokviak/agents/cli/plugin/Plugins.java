package cz.krokviak.agents.cli.plugin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

public final class Plugins {
    private static final Logger log = LoggerFactory.getLogger(Plugins.class);
    private Plugins() {}

    private static final List<LoadedPlugin> loadedPlugins = new ArrayList<>();

    /**
     * Load all plugins: Java SPI plugins + directory-based plugins from .krok/plugins/.
     */
    public static void loadAll(PluginContext context, Path cwd) {
        // 1. Java SPI plugins (classpath-based)
        ServiceLoader<CliPlugin> loader = ServiceLoader.load(CliPlugin.class);
        for (CliPlugin plugin : loader) {
            try {
                plugin.register(context);
                context.cliContext().output().println("  Loaded plugin: " + plugin.name());
            } catch (Exception e) {
                log.warn( "Failed to load SPI plugin " + plugin.name() + ": " + e.getMessage());
            }
        }

        // 2. Directory-based plugins (.krok/plugins/)
        List<LoadedPlugin> dirPlugins = PluginLoader.loadAll(cwd);
        for (LoadedPlugin plugin : dirPlugins) {
            try {
                // Register commands
                for (var cmd : plugin.commands()) {
                    context.addCommand(cmd);
                }
                // Register skills
                for (var skill : plugin.skills()) {
                    context.addSkill(skill);
                }
                // Register hooks
                for (var hook : plugin.hooks()) {
                    context.addHook(hook);
                }

                loadedPlugins.add(plugin);
                context.cliContext().output().println("  Loaded plugin: " + plugin.name()
                    + " (" + plugin.commands().size() + " commands, "
                    + plugin.skills().size() + " skills, "
                    + plugin.hooks().size() + " hooks)");
            } catch (Exception e) {
                log.warn( "Failed to register plugin " + plugin.name() + ": " + e.getMessage());
            }
        }
    }

    /**
     * Legacy method for backward compatibility.
     */
    public static void loadAll(PluginContext context) {
        loadAll(context, context.cliContext().workingDirectory());
    }

    public static List<LoadedPlugin> loaded() { return List.copyOf(loadedPlugins); }
}
