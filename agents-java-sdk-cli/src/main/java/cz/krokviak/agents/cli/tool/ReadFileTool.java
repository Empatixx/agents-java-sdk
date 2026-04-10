package cz.krokviak.agents.cli.tool;

import cz.krokviak.agents.context.ToolContext;
import cz.krokviak.agents.tool.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class ReadFileTool implements ExecutableTool {

    private static final int DEFAULT_LIMIT = 200;
    private static final int MAX_OUTPUT_CHARS = 10_000;
    private static final long MAX_FILE_SIZE = 256 * 1024; // 256 KB

    private final Path workingDirectory;
    private final ToolDefinition toolDefinition;

    public ReadFileTool(Path workingDirectory) {
        this.workingDirectory = workingDirectory;
        this.toolDefinition = new ToolDefinition(
            "read_file",
            "Read a file. Returns lines with line number prefixes. " +
                "Default: first 200 lines. Use offset/limit for large files. " +
                "Max output 10K chars — use smaller ranges for big files.",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "file_path", Map.of("type", "string", "description", "Absolute or relative path"),
                    "offset", Map.of("type", "integer", "description", "Start line (0-based, default 0)"),
                    "limit", Map.of("type", "integer", "description", "Max lines to read (default 200)")
                ),
                "required", List.of("file_path")
            )
        );
    }

    @Override public String name() { return "read_file"; }
    @Override public String description() { return toolDefinition.description(); }
    @Override public ToolDefinition definition() { return toolDefinition; }

    @Override
    public ToolOutput execute(ToolArgs args, ToolContext<?> ctx) throws Exception {
        String filePath = args.get("file_path", String.class);
        int offset = getInt(args, "offset", 0);
        int limit = getInt(args, "limit", DEFAULT_LIMIT);

        Path resolved = workingDirectory.resolve(filePath).normalize();
        if (!Files.exists(resolved)) {
            return ToolOutput.text("Error: File not found: " + resolved);
        }
        if (Files.isDirectory(resolved)) {
            return ToolOutput.text("Error: Path is a directory. Use list_directory instead.");
        }

        // Check file size
        long size = Files.size(resolved);
        if (size > MAX_FILE_SIZE && offset == 0 && limit >= DEFAULT_LIMIT) {
            long totalLines = Files.lines(resolved).count();
            return ToolOutput.text("Error: File too large (" + (size / 1024) + " KB, " + totalLines + " lines). "
                + "Use offset and limit to read specific sections. "
                + "Example: read_file(file_path=\"" + filePath + "\", offset=0, limit=100)");
        }

        List<String> allLines = Files.readAllLines(resolved);
        int totalLines = allLines.size();
        int end = Math.min(offset + limit, totalLines);

        StringBuilder sb = new StringBuilder();
        for (int i = offset; i < end; i++) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(i + 1).append('\t').append(allLines.get(i));

            // Truncate at char limit
            if (sb.length() > MAX_OUTPUT_CHARS) {
                sb.setLength(MAX_OUTPUT_CHARS);
                sb.append("\n... (output truncated at ").append(MAX_OUTPUT_CHARS).append(" chars. ")
                    .append("Total: ").append(totalLines).append(" lines. ")
                    .append("Read lines ").append(offset + 1).append("-").append(i + 1)
                    .append(". Use offset=").append(i + 1).append(" to continue.)");
                break;
            }
        }

        // Add context about remaining lines
        if (end < totalLines) {
            sb.append("\n\n(Showing lines ").append(offset + 1).append("-").append(end)
                .append(" of ").append(totalLines)
                .append(". Use offset=").append(end).append(" to read more.)");
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
