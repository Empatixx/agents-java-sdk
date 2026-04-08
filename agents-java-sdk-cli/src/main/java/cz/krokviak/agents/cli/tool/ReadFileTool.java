package cz.krokviak.agents.cli.tool;

import cz.krokviak.agents.context.ToolContext;
import cz.krokviak.agents.tool.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
public class ReadFileTool implements ExecutableTool {

    private final Path workingDirectory;
    private final ToolDefinition toolDefinition;

    public ReadFileTool(Path workingDirectory) {
        this.workingDirectory = workingDirectory;
        this.toolDefinition = new ToolDefinition(
            "read_file",
            "Read a file from the local filesystem. Returns lines with line number prefixes.",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "file_path", Map.of("type", "string", "description", "Absolute or relative path to the file"),
                    "offset", Map.of("type", "integer", "description", "Line offset to start reading from (0-based, default 0)"),
                    "limit", Map.of("type", "integer", "description", "Maximum number of lines to read (default 2000)")
                ),
                "required", List.of("file_path")
            )
        );
    }

    @Override
    public String name() { return "read_file"; }

    @Override
    public String description() { return toolDefinition.description(); }

    @Override
    public ToolDefinition definition() { return toolDefinition; }

    @Override
    public ToolOutput execute(ToolArgs args, ToolContext<?> ctx) throws Exception {
        String filePath = args.get("file_path", String.class);
        int offset = getInt(args, "offset", 0);
        int limit = getInt(args, "limit", 2000);

        Path resolved = workingDirectory.resolve(filePath).normalize();
        if (!Files.exists(resolved)) {
            return ToolOutput.text("Error: File not found: " + resolved);
        }
        if (Files.isDirectory(resolved)) {
            return ToolOutput.text("Error: Path is a directory: " + resolved);
        }

        List<String> allLines = Files.readAllLines(resolved);
        StringBuilder sb = new StringBuilder();
        int end = Math.min(offset + limit, allLines.size());
        for (int i = offset; i < end; i++) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(i + 1).append('\t').append(allLines.get(i));
        }

        return ToolOutput.text(sb.toString());
    }

    private static int getInt(ToolArgs args, String key, int defaultValue) {
        Object val = args.raw().get(key);
        if (val == null) return defaultValue;
        if (val instanceof Number n) return n.intValue();
        return Integer.parseInt(val.toString());
    }
}
