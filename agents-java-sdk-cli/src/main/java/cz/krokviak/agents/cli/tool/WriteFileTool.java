package cz.krokviak.agents.cli.tool;

import cz.krokviak.agents.context.ToolContext;
import cz.krokviak.agents.tool.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class WriteFileTool implements ExecutableTool {

    private final Path workingDirectory;
    private final ToolDefinition toolDefinition;

    public WriteFileTool(Path workingDirectory) {
        this.workingDirectory = workingDirectory;
        this.toolDefinition = new ToolDefinition(
            "write_file",
            "Write content to a file, creating parent directories if needed.",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "file_path", Map.of("type", "string", "description", "Absolute or relative path to the file"),
                    "content", Map.of("type", "string", "description", "The content to write to the file")
                ),
                "required", List.of("file_path", "content")
            )
        );
    }

    @Override
    public String name() { return "write_file"; }

    @Override
    public String description() { return toolDefinition.description(); }

    @Override
    public ToolDefinition definition() { return toolDefinition; }

    @Override
    public ToolOutput execute(ToolArgs args, ToolContext<?> ctx) throws Exception {
        String filePath = args.get("file_path", String.class);
        String content = args.get("content", String.class);

        Path resolved = workingDirectory.resolve(filePath).normalize();
        Files.createDirectories(resolved.getParent());
        Files.writeString(resolved, content);

        long bytes = Files.size(resolved);
        return ToolOutput.text("Successfully wrote " + bytes + " bytes to " + resolved);
    }
}
