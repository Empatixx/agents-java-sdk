package cz.krokviak.agents.cli.plugin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import cz.krokviak.agents.cli.CliContext;
import cz.krokviak.agents.cli.command.Command;
import cz.krokviak.agents.cli.hook.Hook;
import cz.krokviak.agents.cli.hook.HookResult;
import cz.krokviak.agents.cli.hook.ToolUseEvent;
import cz.krokviak.agents.cli.skill.Skill;
import cz.krokviak.agents.cli.skill.SkillLoader;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Discovers and loads plugins from .krok/plugins/ directories.
 *
 * Plugin directory structure:
 * <pre>
 *   my-plugin/
 *   ├── plugin.json          # Manifest (required)
 *   ├── commands/             # Slash commands (*.md)
 *   ├── skills/               # Skills (*.md with frontmatter)
 *   ├── agents/               # Agent definitions (*.md)
 *   └── hooks.json            # Hook definitions (optional)
 * </pre>
 */
public final class PluginLoader {
    private static final Logger log = LoggerFactory.getLogger(PluginLoader.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private PluginLoader() {}

    /**
     * Load all plugins from global (~/.krok/plugins/) and project (.krok/plugins/) directories.
     */
    public static List<LoadedPlugin> loadAll(Path cwd) {
        List<LoadedPlugin> plugins = new ArrayList<>();

        // Global plugins
        Path globalPlugins = Path.of(System.getProperty("user.home"), ".krok", "plugins");
        plugins.addAll(loadFromDirectory(globalPlugins));

        // Project plugins
        Path projectPlugins = cwd.resolve(".krok").resolve("plugins");
        plugins.addAll(loadFromDirectory(projectPlugins));

        return plugins;
    }

    /**
     * Load all plugin directories from a parent directory.
     */
    public static List<LoadedPlugin> loadFromDirectory(Path pluginsDir) {
        List<LoadedPlugin> plugins = new ArrayList<>();
        if (!Files.isDirectory(pluginsDir)) return plugins;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(pluginsDir)) {
            for (Path entry : stream) {
                if (!Files.isDirectory(entry)) continue;
                Path manifestPath = entry.resolve("plugin.json");
                if (!Files.isRegularFile(manifestPath)) continue;

                try {
                    LoadedPlugin plugin = loadPlugin(entry, manifestPath);
                    plugins.add(plugin);
                } catch (Exception e) {
                    log.warn(
                        "Failed to load plugin from " + entry + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            log.warn( "Failed to scan plugins directory: " + pluginsDir, e);
        }

        return plugins;
    }

    /**
     * Load a single plugin from its directory.
     */
    @SuppressWarnings("unchecked")
    public static LoadedPlugin loadPlugin(Path pluginDir, Path manifestPath) throws IOException {
        // Parse manifest
        Map<String, Object> raw = MAPPER.readValue(manifestPath.toFile(), Map.class);
        PluginManifest manifest = new PluginManifest(
            (String) raw.get("name"),
            (String) raw.getOrDefault("version", "0.0.1"),
            (String) raw.get("description"),
            (String) raw.get("author"),
            toStringList(raw.get("commands")),
            toStringList(raw.get("skills")),
            toStringList(raw.get("agents")),
            raw.get("hooks") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of(),
            raw.get("settings") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of()
        );

        // Load commands from commands/ dir and any paths listed in manifest
        List<Command> commands = new ArrayList<>();
        commands.addAll(loadCommands(pluginDir.resolve("commands"), manifest.name()));
        for (String path : manifest.commands()) {
            Path resolved = pluginDir.resolve(path);
            if (Files.isDirectory(resolved)) {
                commands.addAll(loadCommands(resolved, manifest.name()));
            } else if (Files.isRegularFile(resolved) && resolved.toString().endsWith(".md")) {
                loadCommandFile(resolved, manifest.name()).ifPresent(commands::add);
            }
        }

        // Load skills from skills/ dir and any paths listed in manifest
        List<Skill> skills = new ArrayList<>();
        skills.addAll(loadSkills(pluginDir.resolve("skills"), manifest.name()));
        for (String path : manifest.skills()) {
            Path resolved = pluginDir.resolve(path);
            if (Files.isDirectory(resolved)) {
                skills.addAll(loadSkills(resolved, manifest.name()));
            }
        }

        // Load hooks
        List<Hook> hooks = new ArrayList<>();
        Path hooksJson = pluginDir.resolve("hooks.json");
        if (Files.isRegularFile(hooksJson)) {
            hooks.addAll(loadHooksFromJson(hooksJson, pluginDir));
        }
        if (!manifest.hooks().isEmpty()) {
            hooks.addAll(parseHooks(manifest.hooks(), pluginDir));
        }

        return new LoadedPlugin(manifest, pluginDir, commands, skills, hooks);
    }

    // --- Command loading ---

    private static List<Command> loadCommands(Path dir, String pluginName) {
        List<Command> commands = new ArrayList<>();
        if (!Files.isDirectory(dir)) return commands;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.md")) {
            for (Path file : stream) {
                loadCommandFile(file, pluginName).ifPresent(commands::add);
            }
        } catch (IOException ignored) {}

        return commands;
    }

    private static java.util.Optional<Command> loadCommandFile(Path file, String pluginName) {
        try {
            Skill skill = SkillLoader.loadFromPath(file);
            if (skill == null) return java.util.Optional.empty();

            String cmdName = skill.name() != null ? skill.name()
                : file.getFileName().toString().replace(".md", "");
            String qualifiedName = pluginName + ":" + cmdName;
            String desc = skill.description() != null ? skill.description() : "";

            return java.util.Optional.of(new PluginCommand(qualifiedName, cmdName, desc, skill.content()));
        } catch (Exception e) {
            log.warn( "Failed to load command from " + file, e);
            return java.util.Optional.empty();
        }
    }

    // --- Skill loading ---

    private static List<Skill> loadSkills(Path dir, String pluginName) {
        if (!Files.isDirectory(dir)) return List.of();

        List<Skill> skills = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.md")) {
            for (Path file : stream) {
                try {
                    Skill skill = SkillLoader.loadFromPath(file);
                    if (skill != null) {
                        // Prefix skill name with plugin name
                        String qualifiedName = pluginName + ":" + skill.name();
                        skills.add(new Skill(qualifiedName, skill.description(),
                            skill.content(), skill.metadata(), skill.sourcePath()));
                    }
                } catch (Exception e) {
                    log.warn( "Failed to load skill from " + file, e);
                }
            }
        } catch (IOException ignored) {}

        return skills;
    }

    // --- Hook loading ---

    @SuppressWarnings("unchecked")
    private static List<Hook> loadHooksFromJson(Path hooksJson, Path pluginDir) {
        try {
            Map<String, Object> raw = MAPPER.readValue(hooksJson.toFile(), Map.class);
            Object hooksObj = raw.get("hooks");
            if (hooksObj instanceof Map<?, ?> hooks) {
                return parseHooks((Map<String, Object>) hooks, pluginDir);
            }
        } catch (Exception e) {
            log.warn( "Failed to parse hooks.json: " + hooksJson, e);
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private static List<Hook> parseHooks(Map<String, Object> hooksMap, Path pluginDir) {
        List<Hook> result = new ArrayList<>();

        for (var entry : hooksMap.entrySet()) {
            String eventName = entry.getKey();
            Hook.Phase phase = switch (eventName) {
                case "PreToolUse" -> Hook.Phase.PRE_TOOL;
                case "PostToolUse" -> Hook.Phase.POST_TOOL;
                default -> null;
            };
            if (phase == null) continue;

            if (entry.getValue() instanceof List<?> matchers) {
                for (Object matcherObj : matchers) {
                    if (matcherObj instanceof Map<?, ?> matcherMap) {
                        String matcher = (String) matcherMap.get("matcher");
                        Object hooksList = matcherMap.get("hooks");
                        if (hooksList instanceof List<?> hookDefs) {
                            for (Object hookDef : hookDefs) {
                                if (hookDef instanceof Map<?, ?> hookMap) {
                                    String type = (String) hookMap.get("type");
                                    if ("command".equals(type)) {
                                        String command = (String) hookMap.get("command");
                                        int timeout = hookMap.get("timeout") instanceof Number n ? n.intValue() : 10;
                                        if (command != null) {
                                            result.add(new CommandHook(phase, matcher, command, pluginDir, timeout));
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return result;
    }

    // --- Helpers ---

    @SuppressWarnings("unchecked")
    private static List<String> toStringList(Object obj) {
        if (obj instanceof List<?> list) {
            return list.stream().map(Object::toString).toList();
        }
        return List.of();
    }

    // --- Inner classes ---

    /**
     * A slash command loaded from a plugin's markdown file.
     */
    private record PluginCommand(String qualifiedName, String shortName, String desc, String content)
            implements Command {
        @Override public String name() { return qualifiedName; }
        @Override public List<String> aliases() { return List.of(shortName); }
        @Override public String description() { return desc; }
        @Override
        public void execute(String args, cz.krokviak.agents.cli.CliContext ctx) {
            String output = "<command-name>" + qualifiedName + "</command-name>\n" + content;
            if (args != null && !args.isBlank()) {
                output += "\n\nARGUMENTS: " + args;
            }
            ctx.output().println(output);
        }
    }

    /**
     * A shell command hook loaded from plugin's hooks.json.
     */
    private record CommandHook(Hook.Phase phase, String matcher, String command,
                               Path pluginDir, int timeoutSeconds) implements Hook {
        @Override
        public Hook.Phase phase() { return phase; }

        @Override
        public HookResult execute(ToolUseEvent event) {
            // Check matcher pattern
            if (matcher != null && !matcher.isBlank()) {
                String toolPattern = event.toolName();
                if (!matchesPattern(matcher, toolPattern, event.args())) {
                    return new HookResult.Proceed();
                }
            }

            try {
                String expandedCommand = command
                    .replace("${KROK_PLUGIN_ROOT}", pluginDir.toAbsolutePath().toString())
                    .replace("$TOOL_NAME", event.toolName())
                    .replace("$TOOL_CALL_ID", event.toolCallId() != null ? event.toolCallId() : "");

                ProcessBuilder pb = new ProcessBuilder("bash", "-c", expandedCommand);
                pb.directory(pluginDir.toFile());
                pb.redirectErrorStream(true);
                Process process = pb.start();

                boolean finished = process.waitFor(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    return new HookResult.Block("Hook timed out: " + command);
                }

                String output = new String(process.getInputStream().readAllBytes()).trim();
                int exitCode = process.exitValue();

                if (exitCode != 0) {
                    return new HookResult.Block("Hook failed (exit " + exitCode + "): " + output);
                }

                // Try to parse JSON response
                if (output.startsWith("{")) {
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> response = MAPPER.readValue(output, Map.class);
                        Boolean cont = (Boolean) response.getOrDefault("continue", true);
                        if (Boolean.FALSE.equals(cont)) {
                            String reason = (String) response.getOrDefault("stopReason", "Blocked by hook");
                            return new HookResult.Block(reason);
                        }
                    } catch (Exception ignored) {} // Not JSON — that's OK
                }

                return new HookResult.Proceed();
            } catch (Exception e) {
                return new HookResult.Block("Hook error: " + e.getMessage());
            }
        }

        private static boolean matchesPattern(String pattern, String toolName, Map<String, Object> args) {
            // Pattern format: "ToolName(arg_pattern)" e.g. "Bash(git *)"
            int parenIdx = pattern.indexOf('(');
            if (parenIdx < 0) {
                return toolName.equalsIgnoreCase(pattern);
            }
            String patternTool = pattern.substring(0, parenIdx).trim();
            if (!toolName.equalsIgnoreCase(patternTool) && !"*".equals(patternTool)) {
                return false;
            }
            String argPattern = pattern.substring(parenIdx + 1, pattern.length() - 1).trim();
            if ("*".equals(argPattern)) return true;

            // Match against first string argument (usually "command" for Bash)
            String firstArg = args.values().stream()
                .filter(v -> v instanceof String)
                .map(Object::toString)
                .findFirst().orElse("");

            // Simple glob: "git *" matches "git status", "git push", etc.
            String regex = argPattern.replace("*", ".*");
            return firstArg.matches(regex);
        }
    }
}
