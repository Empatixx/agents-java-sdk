package cz.krokviak.agents.cli.tool;

import cz.krokviak.agents.cli.memory.Memory;
import cz.krokviak.agents.cli.memory.MemoryStore;
import cz.krokviak.agents.cli.memory.MemoryType;
import cz.krokviak.agents.context.ToolContext;
import cz.krokviak.agents.tool.*;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MemoryWriteTool implements ExecutableTool {

    private final MemoryStore memoryStore;
    private final ToolDefinition toolDefinition;

    public MemoryWriteTool(MemoryStore memoryStore) {
        this.memoryStore = memoryStore;
        this.toolDefinition = new ToolDefinition(
            "memory_write",
            "Save a memory entry to the persistent memory store. " +
                "Use this to remember important facts, preferences, or feedback for future conversations.",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "name", Map.of("type", "string", "description", "Unique name/title for this memory"),
                    "description", Map.of("type", "string", "description", "Short one-line description"),
                    "type", Map.of("type", "string", "description", "Memory type: user, feedback, project, or reference",
                        "enum", List.of("user", "feedback", "project", "reference")),
                    "content", Map.of("type", "string", "description", "Full content of the memory")
                ),
                "required", List.of("name", "description", "type", "content")
            )
        );
    }

    @Override public String name() { return "memory_write"; }
    @Override public String description() { return toolDefinition.description(); }
    @Override public ToolDefinition definition() { return toolDefinition; }

    @Override
    public ToolOutput execute(ToolArgs args, ToolContext<?> ctx) throws Exception {
        String name = args.get("name", String.class);
        String description = args.get("description", String.class);
        String typeStr = args.get("type", String.class);
        String content = args.get("content", String.class);

        if (name == null || name.isBlank()) return ToolOutput.text("Error: name is required");
        if (content == null || content.isBlank()) return ToolOutput.text("Error: content is required");

        MemoryType type;
        try {
            type = MemoryType.valueOf((typeStr != null ? typeStr : "user").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            type = MemoryType.USER;
        }

        String filename = toFilename(name);
        Memory memory = new Memory(name, description != null ? description : "", type, content, filename);
        memoryStore.saveMemory(memory);
        return ToolOutput.text("Memory saved: " + name + " → " + filename);
    }

    private static String toFilename(String name) {
        return name.toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "_")
            .replaceAll("^_|_$", "")
            + ".md";
    }
}
