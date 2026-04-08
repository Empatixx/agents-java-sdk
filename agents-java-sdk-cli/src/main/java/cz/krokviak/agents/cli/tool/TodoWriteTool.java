package cz.krokviak.agents.cli.tool;

import cz.krokviak.agents.context.ToolContext;
import cz.krokviak.agents.tool.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class TodoWriteTool implements ExecutableTool {
    private static final Path TODOS_PATH = Path.of(System.getProperty("user.home"), ".claude-cli", "todos.json");

    private final ToolDefinition toolDefinition;

    public TodoWriteTool() {
        this.toolDefinition = new ToolDefinition("todo_write",
            "Write the full todo list to disk (overwrites). Each todo has: id, content, status, priority.",
            Map.of("type", "object", "properties", Map.of(
                "todos", Map.of("type", "string",
                    "description", "JSON array of todo objects with fields: id (string), content (string), status (pending/in_progress/completed), priority (high/medium/low)")
            ), "required", List.of("todos")));
    }

    @Override public String name() { return "todo_write"; }
    @Override public String description() { return toolDefinition.description(); }
    @Override public ToolDefinition definition() { return toolDefinition; }

    @Override
    public ToolOutput execute(ToolArgs args, ToolContext<?> ctx) {
        String todos = args.get("todos", String.class);
        if (todos == null || todos.isBlank()) return ToolOutput.text("Error: todos required");

        // Basic validation: must start with '[' after trimming
        String trimmed = todos.trim();
        if (!trimmed.startsWith("[")) {
            return ToolOutput.text("Error: todos must be a JSON array");
        }

        try {
            Files.createDirectories(TODOS_PATH.getParent());
            Files.writeString(TODOS_PATH, trimmed, StandardCharsets.UTF_8);
            // Count items by counting top-level objects (naive: count '{' at depth 1)
            int count = countItems(trimmed);
            return ToolOutput.text("Todo list saved: " + count + " item(s) written to " + TODOS_PATH);
        } catch (IOException e) {
            return ToolOutput.text("Error writing todos: " + e.getMessage());
        }
    }

    private int countItems(String json) {
        int count = 0;
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escape) { escape = false; continue; }
            if (c == '\\' && inString) { escape = true; continue; }
            if (c == '"') { inString = !inString; continue; }
            if (inString) continue;
            if (c == '{') { depth++; if (depth == 2) count++; }
            else if (c == '}') depth--;
        }
        return count;
    }
}
