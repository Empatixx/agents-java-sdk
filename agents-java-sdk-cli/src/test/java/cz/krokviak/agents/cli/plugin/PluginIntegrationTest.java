package cz.krokviak.agents.cli.plugin;

import cz.krokviak.agents.api.hook.HookPhase;

import cz.krokviak.agents.cli.command.Command;
import cz.krokviak.agents.cli.command.Commands;
import cz.krokviak.agents.agent.hook.Hook;
import cz.krokviak.agents.agent.hook.Hooks;
import cz.krokviak.agents.agent.hook.ToolUseEvent;
import cz.krokviak.agents.cli.skill.Skill;
import cz.krokviak.agents.cli.skill.SkillRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test: loads a plugin and verifies commands, skills, hooks
 * are correctly registered into the global registries.
 */
class PluginIntegrationTest {

    @TempDir
    Path tempDir;

    Commands commands;
    Hooks hooks;
    SkillRegistry skillRegistry;

    @BeforeEach
    void setup() {
        commands = new Commands();
        hooks = new Hooks();
        skillRegistry = new SkillRegistry();
    }

    @Test
    void pluginCommandsRegisteredAndExecutable() throws IOException {
        Path pluginDir = createPlugin("demo", """
            { "name": "demo", "description": "Demo plugin" }
            """);
        Files.createDirectories(pluginDir.resolve("commands"));
        Files.writeString(pluginDir.resolve("commands").resolve("hello.md"), """
            ---
            name: hello
            description: Say hello
            ---
            Greet the user warmly.
            """);

        // Load and register
        var loaded = PluginLoader.loadFromDirectory(tempDir.resolve("plugins"));
        assertEquals(1, loaded.size());

        for (var plugin : loaded) {
            for (var cmd : plugin.commands()) {
                commands.register(cmd);
            }
        }

        // Verify command registered
        Command cmd = commands.find("demo:hello");
        assertNotNull(cmd, "Command should be found by qualified name");
        assertEquals("Say hello", cmd.description());

        // Also findable by short alias
        Command alias = commands.find("hello");
        assertNotNull(alias, "Command should be found by short alias");
    }

    @Test
    void pluginSkillsRegisteredAndSearchable() throws IOException {
        Path pluginDir = createPlugin("tools", """
            { "name": "tools" }
            """);
        Files.createDirectories(pluginDir.resolve("skills"));
        Files.writeString(pluginDir.resolve("skills").resolve("lint.md"), """
            ---
            name: lint
            description: Run linter on code
            ---
            Execute the project linter and report issues.
            """);

        var loaded = PluginLoader.loadFromDirectory(tempDir.resolve("plugins"));
        for (var plugin : loaded) {
            for (var skill : plugin.skills()) {
                skillRegistry.register(skill);
            }
        }

        // Verify skill registered
        var skill = skillRegistry.get("tools:lint");
        assertTrue(skill.isPresent());
        assertEquals("Run linter on code", skill.get().description());
        assertTrue(skill.get().content().contains("Execute the project linter"));

        // Verify searchable
        var results = skillRegistry.search("lint");
        assertFalse(results.isEmpty());
    }

    @Test
    void pluginHooksRegisteredAndFire() throws IOException {
        Path pluginDir = createPlugin("guard", """
            {
              "name": "guard",
              "hooks": {
                "PreToolUse": [
                  {
                    "matcher": "Bash(rm *)",
                    "hooks": [
                      { "type": "command", "command": "echo '{\\\"continue\\\": false, \\\"stopReason\\\": \\\"blocked rm\\\"}'", "timeout": 5 }
                    ]
                  }
                ]
              }
            }
            """);

        var loaded = PluginLoader.loadFromDirectory(tempDir.resolve("plugins"));
        for (var plugin : loaded) {
            for (var hook : plugin.hooks()) {
                hooks.register(hook);
            }
        }

        // Hook should block "rm -rf /"
        var event = ToolUseEvent.preTool("Bash", Map.of("command", "rm -rf /"), null, "tc-1");
        var result = hooks.dispatch(HookPhase.PRE_TOOL, event);
        assertInstanceOf(cz.krokviak.agents.api.hook.HookResult.Block.class, result);

        // Hook should NOT block "echo hello"
        var safeEvent = ToolUseEvent.preTool("Bash", Map.of("command", "echo hello"), null, "tc-2");
        var safeResult = hooks.dispatch(HookPhase.PRE_TOOL, safeEvent);
        assertInstanceOf(cz.krokviak.agents.api.hook.HookResult.Proceed.class, safeResult);
    }

    @Test
    void fullPluginWithAllComponents() throws IOException {
        Path pluginDir = createPlugin("full", """
            { "name": "full", "description": "Full plugin" }
            """);
        Files.createDirectories(pluginDir.resolve("commands"));
        Files.createDirectories(pluginDir.resolve("skills"));
        Files.writeString(pluginDir.resolve("commands").resolve("deploy.md"), """
            ---
            name: deploy
            description: Deploy app
            ---
            Deploy the application.
            """);
        Files.writeString(pluginDir.resolve("skills").resolve("test.md"), """
            ---
            name: test
            description: Run tests
            ---
            Execute test suite.
            """);
        Files.writeString(pluginDir.resolve("hooks.json"), """
            {
              "hooks": {
                "PostToolUse": [
                  { "hooks": [{ "type": "command", "command": "echo done", "timeout": 3 }] }
                ]
              }
            }
            """);

        var loaded = PluginLoader.loadFromDirectory(tempDir.resolve("plugins"));
        assertEquals(1, loaded.size());

        var plugin = loaded.getFirst();
        assertEquals("full", plugin.name());
        assertEquals(1, plugin.commands().size());
        assertEquals(1, plugin.skills().size());
        assertEquals(1, plugin.hooks().size());
    }

    private Path createPlugin(String name, String manifestJson) throws IOException {
        Path pluginsDir = tempDir.resolve("plugins");
        Path pluginDir = pluginsDir.resolve(name);
        Files.createDirectories(pluginDir);
        Files.writeString(pluginDir.resolve("plugin.json"), manifestJson);
        return pluginDir;
    }
}
