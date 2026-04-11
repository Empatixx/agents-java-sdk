package cz.krokviak.agents.cli.plugin;

import java.util.List;
import java.util.Map;

/**
 * Parsed plugin.json manifest.
 *
 * Example plugin.json:
 * <pre>
 * {
 *   "name": "my-plugin",
 *   "version": "1.0.0",
 *   "description": "What this plugin does",
 *   "author": "Author Name",
 *   "commands": ["./commands"],
 *   "skills": ["./skills"],
 *   "agents": ["./agents"],
 *   "hooks": {},
 *   "settings": {}
 * }
 * </pre>
 */
public record PluginManifest(
    String name,
    String version,
    String description,
    String author,
    List<String> commands,
    List<String> skills,
    List<String> agents,
    Map<String, Object> hooks,
    Map<String, Object> settings
) {
    public PluginManifest {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Plugin name is required");
        if (commands == null) commands = List.of();
        if (skills == null) skills = List.of();
        if (agents == null) agents = List.of();
        if (hooks == null) hooks = Map.of();
        if (settings == null) settings = Map.of();
    }
}
