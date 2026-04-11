package cz.krokviak.agents.cli.command.builtin;

import cz.krokviak.agents.cli.CliContext;
import cz.krokviak.agents.cli.command.Command;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class MemoryCommand implements Command {
    @Override public String name() { return "memory"; }
    @Override public String description() { return "Show loaded AGENTS.md memory files"; }
    @Override public void execute(String args, CliContext ctx) {
        ctx.output().println("Memory files:");

        Path global = Path.of(System.getProperty("user.home"), ".krok", "AGENTS.md");
        checkFile(ctx, "Global", global);

        Path projectDot = ctx.workingDirectory().resolve(".krok").resolve("AGENTS.md");
        checkFile(ctx, "Project (.krok/)", projectDot);

        Path projectRoot = ctx.workingDirectory().resolve("AGENTS.md");
        checkFile(ctx, "Project (root)", projectRoot);
    }

    private void checkFile(CliContext ctx, String label, Path path) {
        if (Files.isRegularFile(path)) {
            try {
                long lines = Files.lines(path).count();
                long bytes = Files.size(path);
                ctx.output().println("  \033[32m✓\033[0m " + label + ": " + path +
                    " (" + lines + " lines, " + bytes + " bytes)");
            } catch (IOException e) {
                ctx.output().println("  \033[31m✗\033[0m " + label + ": " + path + " (read error)");
            }
        } else {
            ctx.output().println("  \033[2m-\033[0m " + label + ": not found");
        }
    }
}
