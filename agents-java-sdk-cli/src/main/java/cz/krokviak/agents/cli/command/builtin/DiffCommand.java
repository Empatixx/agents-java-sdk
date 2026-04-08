package cz.krokviak.agents.cli.command.builtin;

import cz.krokviak.agents.cli.CliContext;
import cz.krokviak.agents.cli.command.Command;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class DiffCommand implements Command {
    @Override public String name() { return "diff"; }
    @Override public String description() { return "Show git diff of current changes"; }
    @Override public void execute(String args, CliContext ctx) {
        try {
            String cmd = args != null && !args.isBlank() ? "git diff " + args : "git diff";
            ProcessBuilder pb = new ProcessBuilder("/bin/bash", "-c", cmd);
            pb.directory(ctx.workingDirectory().toFile());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            p.waitFor();
            if (output.isBlank()) {
                ctx.output().println("No changes.");
            } else {
                // Colorize diff
                for (String line : output.split("\n")) {
                    if (line.startsWith("+") && !line.startsWith("+++")) {
                        ctx.output().println("\033[32m" + line + "\033[0m");
                    } else if (line.startsWith("-") && !line.startsWith("---")) {
                        ctx.output().println("\033[31m" + line + "\033[0m");
                    } else if (line.startsWith("@@")) {
                        ctx.output().println("\033[36m" + line + "\033[0m");
                    } else {
                        ctx.output().println(line);
                    }
                }
            }
        } catch (Exception e) {
            ctx.output().printError("git diff failed: " + e.getMessage());
        }
    }
}
