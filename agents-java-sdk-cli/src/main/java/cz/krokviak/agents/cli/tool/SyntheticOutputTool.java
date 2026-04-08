package cz.krokviak.agents.cli.tool;

import cz.krokviak.agents.context.ToolContext;
import cz.krokviak.agents.tool.*;

import java.util.List;
import java.util.Map;

public class SyntheticOutputTool implements ExecutableTool {
    private final ToolDefinition toolDefinition;

    public SyntheticOutputTool() {
        this.toolDefinition = new ToolDefinition("synthetic_output",
            "Wrap content in format markers. Formats: json, markdown, text.",
            Map.of("type", "object", "properties", Map.of(
                "content", Map.of("type", "string", "description", "The content to wrap"),
                "format", Map.of("type", "string", "description", "Output format: json, markdown, or text"),
                "title", Map.of("type", "string", "description", "Optional title/heading for the output")
            ), "required", List.of("content", "format")));
    }

    @Override public String name() { return "synthetic_output"; }
    @Override public String description() { return toolDefinition.description(); }
    @Override public ToolDefinition definition() { return toolDefinition; }

    @Override
    public ToolOutput execute(ToolArgs args, ToolContext<?> ctx) {
        String content = args.get("content", String.class);
        String format = args.getOrDefault("format", String.class, "text");
        String title = args.getOrDefault("title", String.class, null);

        if (content == null) return ToolOutput.text("Error: content required");
        if (format == null) return ToolOutput.text("Error: format required");

        String result = switch (format.toLowerCase()) {
            case "json" -> {
                String heading = title != null ? "// " + title + "\n" : "";
                yield heading + "```json\n" + content + "\n```";
            }
            case "markdown" -> {
                String heading = title != null ? "# " + title + "\n\n" : "";
                yield heading + content;
            }
            case "text" -> {
                String heading = title != null ? title + "\n" + "=".repeat(title.length()) + "\n\n" : "";
                yield heading + content;
            }
            default -> "Error: unsupported format: " + format + ". Use json, markdown, or text.";
        };

        return ToolOutput.text(result);
    }
}
