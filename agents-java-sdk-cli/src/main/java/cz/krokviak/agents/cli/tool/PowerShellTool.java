package cz.krokviak.agents.cli.tool;

import cz.krokviak.agents.context.ToolContext;
import cz.krokviak.agents.tool.ExecutableTool;
import cz.krokviak.agents.tool.ToolArgs;
import cz.krokviak.agents.tool.ToolDefinition;
import cz.krokviak.agents.tool.ToolOutput;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class PowerShellTool implements ExecutableTool {
    private final Path workingDirectory;
    private final ToolDefinition toolDefinition;

    public PowerShellTool(Path workingDirectory) {
        this.workingDirectory = workingDirectory;
        this.toolDefinition = new ToolDefinition("powershell",
            "Execute a PowerShell command and return its output.",
            Map.of("type", "object", "properties", Map.of(
                "command", Map.of("type", "string", "description", "PowerShell command to execute"),
                "timeout", Map.of("type", "integer", "description", "Timeout in milliseconds (default 120000)")
            ), "required", List.of("command")));
    }

    @Override public String name() { return "powershell"; }
    @Override public String description() { return toolDefinition.description(); }
    @Override public ToolDefinition definition() { return toolDefinition; }

    @Override
    public ToolOutput execute(ToolArgs args, ToolContext<?> ctx) throws Exception {
        String command = args.get("command", String.class);
        int timeout = getInt(args, "timeout", cz.krokviak.agents.cli.CliDefaults.BASH_TIMEOUT_MS);
        if (command == null || command.isBlank()) return ToolOutput.text("Error: command required");

        String shell = detectPowerShellBinary();
        if (shell == null) return ToolOutput.text("Error: PowerShell is not installed (looked for pwsh and powershell)");

        ProcessBuilder pb = new ProcessBuilder(shell, "-NoLogo", "-NoProfile", "-Command", command);
        pb.directory(workingDirectory.toFile());
        pb.redirectErrorStream(true);

        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            return ToolOutput.text("Error starting PowerShell: " + e.getMessage());
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

        return ToolOutput.text((output.isBlank() ? "(no output)" : output) + "\nExit code: " + process.exitValue());
    }

    private static String detectPowerShellBinary() {
        for (String candidate : List.of("pwsh", "powershell")) {
            try {
                Process process = new ProcessBuilder(candidate, "-NoLogo", "-NoProfile", "-Command", "$PSVersionTable.PSVersion.ToString()")
                    .redirectErrorStream(true)
                    .start();
                if (process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0) {
                    return candidate;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static int getInt(ToolArgs args, String key, int defaultValue) {
        Object val = args.raw().get(key);
        if (val == null) return defaultValue;
        if (val instanceof Number n) return n.intValue();
        return Integer.parseInt(val.toString());
    }
}
