package cz.krokviak.agents.cli.plugin;

import cz.krokviak.agents.api.hook.HookPhase;
import cz.krokviak.agents.api.hook.HookResult;
import cz.krokviak.agents.agent.hook.ToolUseEvent;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CommandHookTest {

    @Test
    void matchesExactToolName() {
        assertTrue(CommandHook.matchesPattern("Bash", "bash", Map.of()));
        assertFalse(CommandHook.matchesPattern("Read", "bash", Map.of()));
    }

    @Test
    void wildcardToolMatchesAnything() {
        assertTrue(CommandHook.matchesPattern("*(*)", "bash", Map.of("command", "git status")));
        assertTrue(CommandHook.matchesPattern("*(*)", "write_file", Map.of("file_path", "/tmp/x")));
    }

    @Test
    void globInArgPatternMatchesPrefix() {
        assertTrue(CommandHook.matchesPattern("Bash(git *)", "bash", Map.of("command", "git status")));
        assertTrue(CommandHook.matchesPattern("Bash(git *)", "bash", Map.of("command", "git push origin main")));
        assertFalse(CommandHook.matchesPattern("Bash(git *)", "bash", Map.of("command", "ls")));
    }

    @Test
    void argStarMatchesAnyArg() {
        assertTrue(CommandHook.matchesPattern("Bash(*)", "bash", Map.of("command", "anything goes")));
    }

    @Test
    void bashCommandExecutesAndProceeds() {
        // A hook that runs /bin/true — exit 0, no output, should Proceed.
        CommandHook hook = new CommandHook(
            HookPhase.PRE_TOOL, null, "true", Path.of("/tmp"), 5);
        HookResult result = hook.execute(ToolUseEvent.preTool("bash", Map.of(), null, "tc-1"));
        assertInstanceOf(HookResult.Proceed.class, result);
    }

    @Test
    void nonZeroExitBlocksWithOutput() {
        CommandHook hook = new CommandHook(
            HookPhase.PRE_TOOL, null, "echo denied && exit 1", Path.of("/tmp"), 5);
        HookResult result = hook.execute(ToolUseEvent.preTool("bash", Map.of(), null, "tc-1"));
        assertInstanceOf(HookResult.Block.class, result);
        assertTrue(((HookResult.Block) result).reason().contains("denied"));
    }

    @Test
    void jsonContinueFalseBlocks() {
        CommandHook hook = new CommandHook(
            HookPhase.PRE_TOOL, null,
            "echo '{\"continue\":false,\"stopReason\":\"nope\"}'",
            Path.of("/tmp"), 5);
        HookResult result = hook.execute(ToolUseEvent.preTool("bash", Map.of(), null, "tc-1"));
        assertInstanceOf(HookResult.Block.class, result);
        assertEquals("nope", ((HookResult.Block) result).reason());
    }

    @Test
    void timeoutBlocks() {
        CommandHook hook = new CommandHook(
            HookPhase.PRE_TOOL, null, "sleep 5", Path.of("/tmp"), 1);
        HookResult result = hook.execute(ToolUseEvent.preTool("bash", Map.of(), null, "tc-1"));
        assertInstanceOf(HookResult.Block.class, result);
        assertTrue(((HookResult.Block) result).reason().contains("timed out"));
    }

    @Test
    void nonMatchingPatternSkipsExecutionAndProceeds() {
        // This "command" would fail if it ran, but the matcher excludes it.
        CommandHook hook = new CommandHook(
            HookPhase.PRE_TOOL, "Read", "exit 99", Path.of("/tmp"), 5);
        HookResult result = hook.execute(ToolUseEvent.preTool("bash", Map.of(), null, "tc-1"));
        assertInstanceOf(HookResult.Proceed.class, result);
    }
}
