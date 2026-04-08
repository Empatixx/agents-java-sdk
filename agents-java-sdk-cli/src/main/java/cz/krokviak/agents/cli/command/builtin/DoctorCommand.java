package cz.krokviak.agents.cli.command.builtin;

import cz.krokviak.agents.cli.CliContext;
import cz.krokviak.agents.cli.command.Command;

import java.nio.file.Files;
import java.nio.file.Path;

public class DoctorCommand implements Command {
    @Override public String name() { return "doctor"; }
    @Override public String description() { return "Run diagnostics and check system health"; }
    @Override public void execute(String args, CliContext ctx) {
        ctx.output().println("Running diagnostics...\n");

        // Java version
        String javaVersion = System.getProperty("java.version");
        check(ctx, "Java version", javaVersion, javaVersion.startsWith("25") || javaVersion.startsWith("24") || javaVersion.startsWith("23"));

        // Working directory
        check(ctx, "Working directory", ctx.workingDirectory().toString(),
            Files.isDirectory(ctx.workingDirectory()));

        // Git
        check(ctx, "Git available", checkCommand("git --version", ctx.workingDirectory()),
            true);

        // Model
        check(ctx, "Model", ctx.modelId(), ctx.modelId() != null && !ctx.modelId().isBlank());

        // API key
        check(ctx, "API key set", ctx.apiKey() != null ? "***" + ctx.apiKey().substring(Math.max(0, ctx.apiKey().length() - 4)) : "NOT SET",
            ctx.apiKey() != null && !ctx.apiKey().isBlank());

        // Session
        check(ctx, "Session", ctx.sessionId() != null ? ctx.sessionId() : "none",
            true);

        // CLAUDE.md
        boolean hasClaude = Files.exists(ctx.workingDirectory().resolve("CLAUDE.md")) ||
            Files.exists(ctx.workingDirectory().resolve(".claude").resolve("CLAUDE.md"));
        check(ctx, "CLAUDE.md", hasClaude ? "found" : "not found", true);

        // History size
        check(ctx, "History", ctx.history().size() + " messages", ctx.history().size() < 500);

        // Tasks
        var running = ctx.taskManager().running();
        check(ctx, "Background tasks", running.size() + " running", true);

        ctx.output().println("\nDiagnostics complete.");
    }

    private void check(CliContext ctx, String name, String value, boolean ok) {
        String icon = ok ? "\033[32m✓\033[0m" : "\033[31m✗\033[0m";
        ctx.output().println("  " + icon + " " + name + ": " + value);
    }

    private String checkCommand(String cmd, Path cwd) {
        try {
            ProcessBuilder pb = new ProcessBuilder("/bin/bash", "-c", cmd);
            pb.directory(cwd.toFile());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes()).trim();
            p.waitFor();
            return p.exitValue() == 0 ? output : "NOT FOUND";
        } catch (Exception e) {
            return "NOT FOUND";
        }
    }
}
