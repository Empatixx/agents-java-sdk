package cz.krokviak.agents.cli.tool;

import cz.krokviak.agents.cli.CliContext;
import cz.krokviak.agents.context.ToolContext;
import cz.krokviak.agents.tool.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Creates a git worktree for isolated development.
 * Switches the CLI working directory to the worktree.
 * Use ExitWorktreeTool to return to the original directory.
 */
public class EnterWorktreeTool implements ExecutableTool {
    private static final Logger log = LoggerFactory.getLogger(EnterWorktreeTool.class);

    private final CliContext ctx;
    private final ToolDefinition toolDefinition;

    public EnterWorktreeTool(CliContext ctx) {
        this.ctx = ctx;
        this.toolDefinition = new ToolDefinition("enter_worktree",
            "Create a git worktree for isolated development. " +
                "Switches working directory to the worktree. Use exit_worktree to return.",
            Map.of("type", "object", "properties", Map.of(
                "branch", Map.of("type", "string",
                    "description", "Branch name for the worktree (e.g. 'feature/my-feature')"),
                "base", Map.of("type", "string",
                    "description", "Base branch to create from (default: current branch)")
            ), "required", List.of("branch")));
    }

    @Override public String name() { return "enter_worktree"; }
    @Override public String description() { return toolDefinition.description(); }
    @Override public ToolDefinition definition() { return toolDefinition; }

    @Override
    public ToolOutput execute(ToolArgs args, ToolContext<?> toolCtx) throws Exception {
        String branch = args.get("branch", String.class);
        String base = args.getOrDefault("base", String.class, null);
        if (branch == null || branch.isBlank()) return ToolOutput.text("Error: branch name required");

        // Validate branch name
        if (!branch.matches("[a-zA-Z0-9._/\\-]+")) {
            return ToolOutput.text("Error: invalid branch name. Use a-z, 0-9, ., _, -, /");
        }

        Path repoRoot = ctx.workingDirectory();
        // Check we're in a git repo
        if (!Files.isDirectory(repoRoot.resolve(".git"))) {
            return ToolOutput.text("Error: not in a git repository");
        }

        // Already in a worktree?
        if (ctx.getProperty("worktree_original_cwd") != null) {
            return ToolOutput.text("Error: already in a worktree. Use exit_worktree first.");
        }

        // Worktree path: ../<repo-name>-worktrees/<branch-slug>
        String slug = branch.replaceAll("[/\\\\]", "-");
        Path worktreesDir = repoRoot.getParent().resolve(repoRoot.getFileName() + "-worktrees");
        Path worktreePath = worktreesDir.resolve(slug);

        try {
            // Fetch latest if base specified
            if (base != null && !base.isBlank()) {
                exec(repoRoot, "git", "fetch", "origin", base);
            }

            // Create worktree
            if (Files.isDirectory(worktreePath)) {
                // Already exists — just checkout
                exec(worktreePath, "git", "checkout", branch);
            } else {
                var cmd = new java.util.ArrayList<>(List.of("git", "worktree", "add", "-B", branch,
                    worktreePath.toAbsolutePath().toString()));
                if (base != null && !base.isBlank()) {
                    cmd.add("origin/" + base);
                }
                exec(repoRoot, cmd.toArray(String[]::new));
            }

            // Save original cwd and switch
            ctx.setProperty("worktree_original_cwd", repoRoot.toAbsolutePath().toString());
            ctx.setProperty("worktree_branch", branch);
            ctx.setProperty("worktree_path", worktreePath.toAbsolutePath().toString());
            ctx.setWorkingDirectory(worktreePath);

            return ToolOutput.text("Entered worktree: " + worktreePath.toAbsolutePath()
                + "\nBranch: " + branch
                + "\nUse exit_worktree to return to " + repoRoot.toAbsolutePath());

        } catch (Exception e) {
            log.warn("Failed to create worktree", e);
            return ToolOutput.text("Error creating worktree: " + e.getMessage());
        }
    }

    private void exec(Path cwd, String... command) throws Exception {
        var pb = new ProcessBuilder(command);
        pb.directory(cwd.toFile());
        pb.redirectErrorStream(true);
        var process = pb.start();
        String output = new String(process.getInputStream().readAllBytes()).trim();
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new RuntimeException("Command timed out: " + String.join(" ", command));
        }
        if (process.exitValue() != 0) {
            throw new RuntimeException(output);
        }
    }
}
