package cz.krokviak.agents.cli.tool;

import cz.krokviak.agents.context.ToolContext;
import cz.krokviak.agents.tool.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class BashTool implements ExecutableTool {

    private static final int MAX_OUTPUT_CHARS = 2000;

    private final Path workingDirectory;
    private final ToolDefinition toolDefinition;

    public BashTool(Path workingDirectory) {
        this.workingDirectory = workingDirectory;
        this.toolDefinition = new ToolDefinition(
            "bash",
            "Execute a bash command. Output limited to 2000 chars — use offset_lines/limit_lines for large outputs.",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "command", Map.of("type", "string", "description", "The bash command to execute"),
                    "timeout", Map.of("type", "integer", "description", "Timeout in milliseconds (default 120000)"),
                    "offset_lines", Map.of("type", "integer", "description", "Skip first N lines of output (default 0)"),
                    "limit_lines", Map.of("type", "integer", "description", "Max lines to return (default all, within 2000 char limit)")
                ),
                "required", List.of("command")
            )
        );
    }

    @Override public String name() { return "bash"; }
    @Override public String description() { return toolDefinition.description(); }
    @Override public ToolDefinition definition() { return toolDefinition; }

    @Override
    public ToolOutput execute(ToolArgs args, ToolContext<?> ctx) throws Exception {
        String command = args.get("command", String.class);
        int timeout = getInt(args, "timeout", 120_000);
        int offsetLines = getInt(args, "offset_lines", 0);
        int limitLines = getInt(args, "limit_lines", -1);

        ProcessBuilder pb = new ProcessBuilder("/bin/bash", "-c", command);
        pb.directory(workingDirectory.toFile());
        pb.redirectErrorStream(true);

        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            return ToolOutput.text("Error starting process: " + e.getMessage());
        }

        String rawOutput;
        try (var is = process.getInputStream()) {
            rawOutput = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }

        boolean finished = process.waitFor(timeout, TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
            return ToolOutput.text("Error: Command timed out after " + timeout + "ms\n"
                + truncateOutput(rawOutput));
        }

        int exitCode = process.exitValue();

        // Apply line offset/limit
        String output = applyLineWindow(rawOutput, offsetLines, limitLines);

        // Truncate to max chars
        int totalLines = rawOutput.split("\n", -1).length;
        boolean truncated = output.length() > MAX_OUTPUT_CHARS;
        if (truncated) {
            output = output.substring(0, MAX_OUTPUT_CHARS)
                + "\n... (output truncated at " + MAX_OUTPUT_CHARS + " chars, "
                + totalLines + " total lines. Use offset_lines/limit_lines to page.)";
        }

        if (output.isEmpty()) {
            return ToolOutput.text("(no output)\nExit code: " + exitCode);
        }
        return ToolOutput.text(output.stripTrailing() + "\nExit code: " + exitCode);
    }

    private String applyLineWindow(String output, int offset, int limit) {
        if (offset == 0 && limit < 0) return output;
        String[] lines = output.split("\n", -1);
        int start = Math.min(offset, lines.length);
        int end = limit > 0 ? Math.min(start + limit, lines.length) : lines.length;
        var sb = new StringBuilder();
        for (int i = start; i < end; i++) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(lines[i]);
        }
        return sb.toString();
    }

    private String truncateOutput(String output) {
        if (output.length() <= MAX_OUTPUT_CHARS) return output;
        return output.substring(0, MAX_OUTPUT_CHARS) + "\n... (truncated)";
    }

    private static int getInt(ToolArgs args, String key, int defaultValue) {
        Object val = args.raw().get(key);
        if (val == null) return defaultValue;
        if (val instanceof Number n) return n.intValue();
        return Integer.parseInt(val.toString());
    }
}
