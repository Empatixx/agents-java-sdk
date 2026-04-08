package cz.krokviak.agents.cli.tool;

import cz.krokviak.agents.context.ToolContext;
import cz.krokviak.agents.tool.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class ConfigTool implements ExecutableTool {
    private static final Path CONFIG_PATH = Path.of(System.getProperty("user.home"), ".claude-cli", "config.properties");
    private static final Set<String> KNOWN_KEYS = Set.of(
        "theme", "model", "permission_mode", "max_turns", "editor_mode"
    );

    private final ToolDefinition toolDefinition;

    public ConfigTool() {
        this.toolDefinition = new ToolDefinition("config",
            "Read or write CLI configuration settings. Supports keys: theme (dark/light), model, permission_mode, max_turns, editor_mode (normal/vim).",
            Map.of("type", "object", "properties", Map.of(
                "action", Map.of("type", "string", "description", "Action to perform: get, set, or list"),
                "key", Map.of("type", "string", "description", "Configuration key (required for get/set)"),
                "value", Map.of("type", "string", "description", "Value to set (required for set)")
            ), "required", List.of("action")));
    }

    @Override public String name() { return "config"; }
    @Override public String description() { return toolDefinition.description(); }
    @Override public ToolDefinition definition() { return toolDefinition; }

    @Override
    public ToolOutput execute(ToolArgs args, ToolContext<?> ctx) {
        String action = args.get("action", String.class);
        if (action == null || action.isBlank()) return ToolOutput.text("Error: action required");

        Properties props = loadProperties();

        return switch (action.toLowerCase()) {
            case "list" -> {
                if (props.isEmpty()) yield ToolOutput.text("No configuration set.");
                StringBuilder sb = new StringBuilder("Configuration:\n");
                props.stringPropertyNames().stream().sorted().forEach(k ->
                    sb.append("  ").append(k).append(" = ").append(props.getProperty(k)).append("\n")
                );
                yield ToolOutput.text(sb.toString().trim());
            }
            case "get" -> {
                String key = args.get("key", String.class);
                if (key == null || key.isBlank()) yield ToolOutput.text("Error: key required for get");
                String val = props.getProperty(key);
                yield val != null
                    ? ToolOutput.text(key + " = " + val)
                    : ToolOutput.text("Key not set: " + key);
            }
            case "set" -> {
                String key = args.get("key", String.class);
                String value = args.get("value", String.class);
                if (key == null || key.isBlank()) yield ToolOutput.text("Error: key required for set");
                if (value == null) yield ToolOutput.text("Error: value required for set");
                props.setProperty(key, value);
                String saveError = saveProperties(props);
                yield saveError != null
                    ? ToolOutput.text("Error saving config: " + saveError)
                    : ToolOutput.text("Set " + key + " = " + value);
            }
            default -> ToolOutput.text("Error: unknown action '" + action + "'. Use get, set, or list.");
        };
    }

    private Properties loadProperties() {
        Properties props = new Properties();
        if (Files.exists(CONFIG_PATH)) {
            try (var reader = Files.newBufferedReader(CONFIG_PATH)) {
                props.load(reader);
            } catch (IOException e) {
                // return empty props on failure
            }
        }
        return props;
    }

    private String saveProperties(Properties props) {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (var writer = Files.newBufferedWriter(CONFIG_PATH)) {
                props.store(writer, "Claude CLI configuration");
            }
            return null;
        } catch (IOException e) {
            return e.getMessage();
        }
    }
}
