package cz.krokviak.agents.cli.tool;

import cz.krokviak.agents.context.ToolContext;
import cz.krokviak.agents.tool.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class EditFileTool implements ExecutableTool {

    private final Path workingDirectory;
    private final ToolDefinition toolDefinition;

    public EditFileTool(Path workingDirectory) {
        this.workingDirectory = workingDirectory;
        this.toolDefinition = new ToolDefinition(
            "edit_file",
            "Perform an exact string replacement in a file. The old_string must appear exactly once.",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "file_path", Map.of("type", "string", "description", "Absolute or relative path to the file"),
                    "old_string", Map.of("type", "string", "description", "The exact text to find and replace"),
                    "new_string", Map.of("type", "string", "description", "The replacement text")
                ),
                "required", List.of("file_path", "old_string", "new_string")
            )
        );
    }

    @Override
    public String name() { return "edit_file"; }

    @Override
    public String description() { return toolDefinition.description(); }

    @Override
    public ToolDefinition definition() { return toolDefinition; }

    @Override
    public ToolOutput execute(ToolArgs args, ToolContext<?> ctx) throws Exception {
        String filePath = args.get("file_path", String.class);
        String oldString = args.get("old_string", String.class);
        String newString = args.get("new_string", String.class);

        Path resolved = workingDirectory.resolve(filePath).normalize();
        if (!Files.exists(resolved)) {
            return ToolOutput.text("Error: File not found: " + resolved);
        }

        String content = Files.readString(resolved);
        int count = countOccurrences(content, oldString);

        if (count == 0) {
            return ToolOutput.text("Error: old_string not found in " + resolved);
        }
        if (count > 1) {
            return ToolOutput.text("Error: old_string found " + count + " times, must be unique. Provide more context.");
        }

        String updated = content.replaceFirst(java.util.regex.Pattern.quote(oldString),
            java.util.regex.Matcher.quoteReplacement(newString));
        Files.writeString(resolved, updated);

        return ToolOutput.text("Successfully edited " + resolved);
    }

    private static int countOccurrences(String text, String search) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(search, idx)) != -1) {
            count++;
            idx += search.length();
        }
        return count;
    }
}
