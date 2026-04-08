package cz.krokviak.agents.cli.tool;

import cz.krokviak.agents.context.ToolContext;
import cz.krokviak.agents.tool.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class ListDirectoryTool implements ExecutableTool {

    private final Path workingDirectory;
    private final ToolDefinition toolDefinition;

    public ListDirectoryTool(Path workingDirectory) {
        this.workingDirectory = workingDirectory;
        this.toolDefinition = new ToolDefinition(
            "list_directory",
            "List the contents of a directory.",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "path", Map.of("type", "string", "description", "Path to the directory to list")
                ),
                "required", List.of("path")
            )
        );
    }

    @Override
    public String name() { return "list_directory"; }

    @Override
    public String description() { return toolDefinition.description(); }

    @Override
    public ToolDefinition definition() { return toolDefinition; }

    @Override
    public ToolOutput execute(ToolArgs args, ToolContext<?> ctx) throws Exception {
        String pathStr = args.get("path", String.class);
        Path resolved = workingDirectory.resolve(pathStr).normalize();

        if (!Files.isDirectory(resolved)) {
            return ToolOutput.text("Error: Not a directory: " + resolved);
        }

        List<Path> entries;
        try (Stream<Path> stream = Files.list(resolved)) {
            entries = stream
                .sorted(Comparator.<Path, Boolean>comparing(p -> !Files.isDirectory(p))
                    .thenComparing(Path::getFileName))
                .toList();
        }

        if (entries.isEmpty()) {
            return ToolOutput.text("(empty directory)");
        }

        StringBuilder sb = new StringBuilder();
        for (Path entry : entries) {
            if (!sb.isEmpty()) sb.append('\n');
            if (Files.isDirectory(entry)) {
                sb.append("[DIR]  ").append(entry.getFileName());
            } else {
                long size = Files.size(entry);
                sb.append("[FILE] ").append(entry.getFileName()).append(" (").append(size).append(" bytes)");
            }
        }
        return ToolOutput.text(sb.toString());
    }
}
