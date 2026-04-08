package cz.krokviak.agents.cli.tool;

import cz.krokviak.agents.cli.memory.Memory;
import cz.krokviak.agents.cli.memory.MemoryStore;
import cz.krokviak.agents.context.ToolContext;
import cz.krokviak.agents.tool.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class MemoryReadTool implements ExecutableTool {

    private final MemoryStore memoryStore;
    private final ToolDefinition toolDefinition;

    public MemoryReadTool(MemoryStore memoryStore) {
        this.memoryStore = memoryStore;
        this.toolDefinition = new ToolDefinition(
            "memory_read",
            "Read memories from the persistent memory store. " +
                "Provide 'name' for exact lookup or 'query' to search by name/description.",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "name", Map.of("type", "string", "description", "Exact memory name to retrieve"),
                    "query", Map.of("type", "string", "description", "Search query to fuzzy-match name/description")
                )
            )
        );
    }

    @Override public String name() { return "memory_read"; }
    @Override public String description() { return toolDefinition.description(); }
    @Override public ToolDefinition definition() { return toolDefinition; }

    @Override
    public ToolOutput execute(ToolArgs args, ToolContext<?> ctx) throws Exception {
        String name = args.raw().containsKey("name") ? args.get("name", String.class) : null;
        String query = args.raw().containsKey("query") ? args.get("query", String.class) : null;

        if (name != null && !name.isBlank()) {
            // Try to find by exact name in index
            Optional<Memory> found = memoryStore.listMemories().stream()
                .filter(m -> m.name().equalsIgnoreCase(name))
                .findFirst()
                .flatMap(m -> memoryStore.loadMemory(m.filePath()));
            if (found.isPresent()) {
                return ToolOutput.text(formatMemory(found.get()));
            }
            // Try direct filename
            String filename = name.endsWith(".md") ? name : name.toLowerCase().replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "") + ".md";
            Optional<Memory> byFile = memoryStore.loadMemory(filename);
            return byFile.map(m -> ToolOutput.text(formatMemory(m)))
                .orElse(ToolOutput.text("No memory found with name: " + name));
        }

        if (query != null && !query.isBlank()) {
            List<Memory> results = memoryStore.searchMemories(query);
            if (results.isEmpty()) return ToolOutput.text("No memories found matching: " + query);
            StringBuilder sb = new StringBuilder();
            sb.append("Found ").append(results.size()).append(" memory(ies):\n\n");
            for (Memory m : results) {
                sb.append(formatMemory(m)).append("\n---\n\n");
            }
            return ToolOutput.text(sb.toString().trim());
        }

        // List all
        List<Memory> all = memoryStore.listMemories();
        if (all.isEmpty()) return ToolOutput.text("No memories stored.");
        StringBuilder sb = new StringBuilder("All memories:\n\n");
        for (Memory m : all) {
            sb.append("- **").append(m.name()).append("** (").append(m.type()).append("): ")
                .append(m.description()).append("\n");
        }
        return ToolOutput.text(sb.toString().trim());
    }

    private static String formatMemory(Memory m) {
        return "# " + m.name() + "\n" +
            "Type: " + m.type() + "\n" +
            "Description: " + m.description() + "\n\n" +
            m.content();
    }
}
