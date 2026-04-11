package cz.krokviak.agents.cli.plugin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PluginLoaderTest {

    @TempDir
    Path tempDir;
    Path pluginDir;

    @BeforeEach
    void setup() throws IOException {
        pluginDir = tempDir.resolve("my-plugin");
        Files.createDirectories(pluginDir.resolve("commands"));
        Files.createDirectories(pluginDir.resolve("skills"));
    }

    @Test
    void loadPluginWithManifest() throws IOException {
        Files.writeString(pluginDir.resolve("plugin.json"), """
            {
              "name": "my-plugin",
              "version": "1.0.0",
              "description": "Test plugin"
            }
            """);

        var plugin = PluginLoader.loadPlugin(pluginDir, pluginDir.resolve("plugin.json"));

        assertEquals("my-plugin", plugin.name());
        assertEquals("Test plugin", plugin.description());
        assertTrue(plugin.commands().isEmpty());
        assertTrue(plugin.skills().isEmpty());
        assertTrue(plugin.hooks().isEmpty());
    }

    @Test
    void loadPluginWithCommand() throws IOException {
        Files.writeString(pluginDir.resolve("plugin.json"), """
            { "name": "cmd-plugin", "description": "Has commands" }
            """);
        Files.writeString(pluginDir.resolve("commands").resolve("build.md"), """
            ---
            name: build
            description: Run the build
            ---

            Execute the project build command.
            """);

        var plugin = PluginLoader.loadPlugin(pluginDir, pluginDir.resolve("plugin.json"));

        assertEquals(1, plugin.commands().size());
        var cmd = plugin.commands().getFirst();
        assertEquals("cmd-plugin:build", cmd.name());
        assertEquals("Run the build", cmd.description());
    }

    @Test
    void loadPluginWithSkill() throws IOException {
        Files.writeString(pluginDir.resolve("plugin.json"), """
            { "name": "skill-plugin" }
            """);
        Files.writeString(pluginDir.resolve("skills").resolve("review.md"), """
            ---
            name: review
            description: Code review skill
            user_invocable: "true"
            ---

            Review code for quality.
            """);

        var plugin = PluginLoader.loadPlugin(pluginDir, pluginDir.resolve("plugin.json"));

        assertEquals(1, plugin.skills().size());
        var skill = plugin.skills().getFirst();
        assertEquals("skill-plugin:review", skill.name());
        assertEquals("Code review skill", skill.description());
        assertTrue(skill.content().contains("Review code for quality"));
    }

    @Test
    void loadPluginWithHooks() throws IOException {
        Files.writeString(pluginDir.resolve("plugin.json"), """
            {
              "name": "hook-plugin",
              "hooks": {
                "PreToolUse": [
                  {
                    "matcher": "Bash(git *)",
                    "hooks": [
                      { "type": "command", "command": "echo ok", "timeout": 5 }
                    ]
                  }
                ]
              }
            }
            """);

        var plugin = PluginLoader.loadPlugin(pluginDir, pluginDir.resolve("plugin.json"));

        assertEquals(1, plugin.hooks().size());
        var hook = plugin.hooks().getFirst();
        assertEquals(cz.krokviak.agents.cli.hook.Hook.Phase.PRE_TOOL, hook.phase());
    }

    @Test
    void loadPluginWithHooksJson() throws IOException {
        Files.writeString(pluginDir.resolve("plugin.json"), """
            { "name": "hooks-json-plugin" }
            """);
        Files.writeString(pluginDir.resolve("hooks.json"), """
            {
              "hooks": {
                "PostToolUse": [
                  {
                    "matcher": "Bash(*)",
                    "hooks": [
                      { "type": "command", "command": "echo done", "timeout": 3 }
                    ]
                  }
                ]
              }
            }
            """);

        var plugin = PluginLoader.loadPlugin(pluginDir, pluginDir.resolve("plugin.json"));

        assertEquals(1, plugin.hooks().size());
        assertEquals(cz.krokviak.agents.cli.hook.Hook.Phase.POST_TOOL, plugin.hooks().getFirst().phase());
    }

    @Test
    void loadFromDirectoryFindsPlugins() throws IOException {
        Path pluginsParent = tempDir.resolve("plugins");
        Files.createDirectories(pluginsParent);

        // Plugin A
        Path pluginA = pluginsParent.resolve("plugin-a");
        Files.createDirectories(pluginA);
        Files.writeString(pluginA.resolve("plugin.json"), """
            { "name": "plugin-a", "description": "First" }
            """);

        // Plugin B
        Path pluginB = pluginsParent.resolve("plugin-b");
        Files.createDirectories(pluginB);
        Files.writeString(pluginB.resolve("plugin.json"), """
            { "name": "plugin-b", "description": "Second" }
            """);

        // Not a plugin (no plugin.json)
        Path notPlugin = pluginsParent.resolve("not-a-plugin");
        Files.createDirectories(notPlugin);
        Files.writeString(notPlugin.resolve("readme.txt"), "just a directory");

        List<LoadedPlugin> plugins = PluginLoader.loadFromDirectory(pluginsParent);

        assertEquals(2, plugins.size());
        var names = plugins.stream().map(LoadedPlugin::name).sorted().toList();
        assertEquals(List.of("plugin-a", "plugin-b"), names);
    }

    @Test
    void emptyDirectoryReturnsNoPlugins() {
        List<LoadedPlugin> plugins = PluginLoader.loadFromDirectory(tempDir.resolve("nonexistent"));
        assertTrue(plugins.isEmpty());
    }

    @Test
    void manifestWithoutNameThrows() {
        assertThrows(Exception.class, () -> {
            Files.writeString(pluginDir.resolve("plugin.json"), """
                { "description": "no name" }
                """);
            PluginLoader.loadPlugin(pluginDir, pluginDir.resolve("plugin.json"));
        });
    }

    @Test
    void commandAliasIsShortName() throws IOException {
        Files.writeString(pluginDir.resolve("plugin.json"), """
            { "name": "my-plugin" }
            """);
        Files.writeString(pluginDir.resolve("commands").resolve("deploy.md"), """
            ---
            name: deploy
            description: Deploy the app
            ---
            Run deployment.
            """);

        var plugin = PluginLoader.loadPlugin(pluginDir, pluginDir.resolve("plugin.json"));
        var cmd = plugin.commands().getFirst();

        assertEquals("my-plugin:deploy", cmd.name());
        assertTrue(cmd.aliases().contains("deploy"));
    }

    @Test
    void multipleCommandsAndSkills() throws IOException {
        Files.writeString(pluginDir.resolve("plugin.json"), """
            { "name": "full-plugin" }
            """);
        Files.writeString(pluginDir.resolve("commands").resolve("cmd1.md"), """
            ---
            name: cmd1
            description: First command
            ---
            Do thing 1.
            """);
        Files.writeString(pluginDir.resolve("commands").resolve("cmd2.md"), """
            ---
            name: cmd2
            description: Second command
            ---
            Do thing 2.
            """);
        Files.writeString(pluginDir.resolve("skills").resolve("skill1.md"), """
            ---
            name: skill1
            description: First skill
            ---
            Skill content 1.
            """);

        var plugin = PluginLoader.loadPlugin(pluginDir, pluginDir.resolve("plugin.json"));

        assertEquals(2, plugin.commands().size());
        assertEquals(1, plugin.skills().size());
    }
}
