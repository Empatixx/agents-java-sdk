package cz.krokviak.agents.cli.tool;

import cz.krokviak.agents.cli.CliContext;
import cz.krokviak.agents.context.ToolContext;
import cz.krokviak.agents.tool.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Exit a git worktree and return to the original working directory.
 * Optionally removes the worktree.
 */
public class ExitWorktreeTool implements ExecutableTool {
    private static final Logger log = LoggerFactory.getLogger(ExitWorktreeTool.class);

    private final CliContext ctx;
    private final ToolDefinition toolDefinition;

    public ExitWorktreeTool(CliContext ctx) {
        this.ctx = ctx;
        this.toolDefinition = new ToolDefinition("exit_worktree",
            "Exit the current git worktree and return to the original directory. " +
                "Set cleanup=true to remove the worktree (default: keep it).",
            Map.of("type", "object", "properties", Map.of(
                "cleanup", Map.of("type", "boolean",
                    "description", "Remove the worktree after exiting (default false)")
            ), "required", List.of()));
    }

    @Override public String name() { return "exit_worktree"; }
    @Override public String description() { return toolDefinition.description(); }
    @Override public ToolDefinition definition() { return toolDefinition; }

    @Override
    public ToolOutput execute(ToolArgs args, ToolContext<?> toolCtx) throws Exception {
        String originalCwd = ctx.getProperty("worktree_original_cwd");
        String worktreePath = ctx.getProperty("worktree_path");
        String branch = ctx.getProperty("worktree_branch");

        if (originalCwd == null) {
            return ToolOutput.text("Error: not in a worktree.");
        }

        Boolean cleanup = args.getOrDefault("cleanup", Boolean.class, false);
        Path original = Path.of(originalCwd);

        // Switch back
        ctx.setWorkingDirectory(original);
        ctx.setProperty("worktree_original_cwd", null);
        ctx.setProperty("worktree_branch", null);
        ctx.setProperty("worktree_path", null);

        var sb = new StringBuilder();
        sb.append("Returned to: ").append(original.toAbsolutePath());
        if (branch != null) sb.append("\nWorktree branch: ").append(branch);

        // Cleanup if requested
        if (Boolean.TRUE.equals(cleanup) && worktreePath != null) {
            try {
                var pb = new ProcessBuilder("git", "worktree", "remove", "--force", worktreePath);
                pb.directory(original.toFile());
                pb.redirectErrorStream(true);
                var process = pb.start();
                String output = new String(process.getInputStream().readAllBytes()).trim();
                process.waitFor(15, TimeUnit.SECONDS);
                if (process.exitValue() == 0) {
                    sb.append("\nWorktree removed.");
                } else {
                    sb.append("\nFailed to remove worktree: ").append(output);
                }
            } catch (Exception e) {
                sb.append("\nFailed to remove worktree: ").append(e.getMessage());
            }
        } else if (worktreePath != null) {
            sb.append("\nWorktree kept at: ").append(worktreePath);
        }

        return ToolOutput.text(sb.toString());
    }
}
