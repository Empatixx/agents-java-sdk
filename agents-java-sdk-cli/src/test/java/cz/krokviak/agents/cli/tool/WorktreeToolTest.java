package cz.krokviak.agents.cli.tool;

import cz.krokviak.agents.cli.CliContext;
import cz.krokviak.agents.cli.test.FakeRenderer;
import cz.krokviak.agents.tool.ToolArgs;
import cz.krokviak.agents.tool.ToolOutput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class WorktreeToolTest {

    @TempDir Path tempDir;
    CliContext ctx;

    @BeforeEach
    void setup() throws Exception {
        // Init a git repo in tempDir
        exec(tempDir, "git", "init");
        exec(tempDir, "git", "config", "user.email", "test@test.com");
        exec(tempDir, "git", "config", "user.name", "Test");
        Files.writeString(tempDir.resolve("readme.md"), "hello");
        exec(tempDir, "git", "add", ".");
        exec(tempDir, "git", "commit", "-m", "initial");

        ctx = new CliContext(null, "test", null, null,
            new FakeRenderer(), null, null, tempDir,
            "", null, null, null, null);
    }

    @Test
    void enterWorktreeCreatesIt() throws Exception {
        var tool = new EnterWorktreeTool(ctx);
        var result = tool.execute(new ToolArgs(Map.of("branch", "feature-x")), null);
        String text = ((ToolOutput.Text) result).content();

        assertTrue(text.contains("Entered worktree"));
        assertTrue(text.contains("feature-x"));
        assertNotEquals(tempDir, ctx.workingDirectory());
        assertTrue(Files.isDirectory(ctx.workingDirectory()));
        assertNotNull(ctx.getProperty("worktree_original_cwd"));
    }

    @Test
    void exitWorktreeRestoresCwd() throws Exception {
        var enter = new EnterWorktreeTool(ctx);
        enter.execute(new ToolArgs(Map.of("branch", "feature-y")), null);

        Path worktreePath = ctx.workingDirectory();
        assertNotEquals(tempDir, worktreePath);

        var exit = new ExitWorktreeTool(ctx);
        var result = exit.execute(new ToolArgs(Map.of()), null);
        String text = ((ToolOutput.Text) result).content();

        assertTrue(text.contains("Returned to"));
        assertEquals(tempDir, ctx.workingDirectory());
        assertNull(ctx.getProperty("worktree_original_cwd"));
    }

    @Test
    void exitWithCleanupRemovesWorktree() throws Exception {
        var enter = new EnterWorktreeTool(ctx);
        enter.execute(new ToolArgs(Map.of("branch", "temp-branch")), null);
        Path worktreePath = ctx.workingDirectory();

        var exit = new ExitWorktreeTool(ctx);
        exit.execute(new ToolArgs(Map.of("cleanup", true)), null);

        assertEquals(tempDir, ctx.workingDirectory());
        // Worktree directory should be removed
        assertFalse(Files.isDirectory(worktreePath));
    }

    @Test
    void enterWhileInWorktreeFails() throws Exception {
        var enter = new EnterWorktreeTool(ctx);
        enter.execute(new ToolArgs(Map.of("branch", "first")), null);

        var result = enter.execute(new ToolArgs(Map.of("branch", "second")), null);
        String text = ((ToolOutput.Text) result).content();
        assertTrue(text.contains("already in a worktree"));
    }

    @Test
    void exitWithoutWorktreeFails() throws Exception {
        var exit = new ExitWorktreeTool(ctx);
        var result = exit.execute(new ToolArgs(Map.of()), null);
        String text = ((ToolOutput.Text) result).content();
        assertTrue(text.contains("not in a worktree"));
    }

    @Test
    void invalidBranchNameRejected() throws Exception {
        var tool = new EnterWorktreeTool(ctx);
        var result = tool.execute(new ToolArgs(Map.of("branch", "bad branch name!!")), null);
        String text = ((ToolOutput.Text) result).content();
        assertTrue(text.contains("invalid branch name"));
    }

    @Test
    void emptyBranchNameRejected() throws Exception {
        var tool = new EnterWorktreeTool(ctx);
        var result = tool.execute(new ToolArgs(Map.of("branch", "")), null);
        String text = ((ToolOutput.Text) result).content();
        assertTrue(text.contains("branch name required"));
    }

    @Test
    void notGitRepoFails() throws Exception {
        Path noGit = tempDir.resolve("not-a-repo");
        Files.createDirectories(noGit);
        ctx.setWorkingDirectory(noGit);

        var tool = new EnterWorktreeTool(ctx);
        var result = tool.execute(new ToolArgs(Map.of("branch", "test")), null);
        String text = ((ToolOutput.Text) result).content();
        assertTrue(text.contains("not in a git repository"));
    }

    private void exec(Path cwd, String... cmd) throws Exception {
        var p = new ProcessBuilder(cmd).directory(cwd.toFile()).redirectErrorStream(true).start();
        p.getInputStream().readAllBytes();
        assertTrue(p.waitFor(10, TimeUnit.SECONDS));
    }
}
