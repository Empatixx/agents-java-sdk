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

    private final Path workingDirectory;
    private final ToolDefinition toolDefinition;

    public BashTool(Path workingDirectory) {
        this.workingDirectory = workingDirectory;
        this.toolDefinition = new ToolDefinition(
            "bash",
            "Execute a bash command and return its output.",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "command", Map.of("type", "string", "description", "The bash command to execute"),
                    "timeout", Map.of("type", "integer", "description", "Timeout in milliseconds (default 120000)")
                ),
                "required", List.of("command")
            )
        );
    }

    @Override
    public String name() { return "bash"; }

    @Override
    public String description() { return toolDefinition.description(); }

    @Override
    public ToolDefinition definition() { return toolDefinition; }

    @Override
    public ToolOutput execute(ToolArgs args, ToolContext<?> ctx) throws Exception {
        String command = args.get("command", String.class);
        int timeout = getInt(args, "timeout", 120_000);

        ProcessBuilder pb = new ProcessBuilder("/bin/bash", "-c", command);
        pb.directory(workingDirectory.toFile());
        pb.redirectErrorStream(true);

        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            return ToolOutput.text("Error starting process: " + e.getMessage());
        }

        String output;
        try (var is = process.getInputStream()) {
            output = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }

        boolean finished = process.waitFor(timeout, TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
            return ToolOutput.text("Error: Command timed out after " + timeout + "ms\nPartial output:\n" + output);
        }

        int exitCode = process.exitValue();
        if (output.isEmpty()) {
            return ToolOutput.text("(no output)\nExit code: " + exitCode);
        }
        return ToolOutput.text(output + "\nExit code: " + exitCode);
    }

    private static int getInt(ToolArgs args, String key, int defaultValue) {
        Object val = args.raw().get(key);
        if (val == null) return defaultValue;
        if (val instanceof Number n) return n.intValue();
        return Integer.parseInt(val.toString());
    }
}
