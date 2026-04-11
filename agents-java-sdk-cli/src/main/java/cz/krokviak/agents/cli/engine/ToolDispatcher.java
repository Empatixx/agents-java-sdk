package cz.krokviak.agents.cli.engine;

import cz.krokviak.agents.cli.CliContext;
import cz.krokviak.agents.cli.hook.*;
import cz.krokviak.agents.cli.tool.ToolClassifier;
import cz.krokviak.agents.runner.InputItem;
import cz.krokviak.agents.runner.RunItem;
import cz.krokviak.agents.tool.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

public class ToolDispatcher {
    private final List<ExecutableTool> tools;
    private final Hooks hooks;
    private final CliContext ctx;

    public ToolDispatcher(List<ExecutableTool> tools, Hooks hooks, CliContext ctx) {
        this.tools = tools;
        this.hooks = hooks;
        this.ctx = ctx;
    }

    /**
     * Shared tool execution with hook dispatch. Used by both ToolDispatcher and StreamingToolExecutor.
     * Returns the result text. Does NOT add to history or print — caller handles that.
     */
    public String executeTool(String toolName, Map<String, Object> arguments, String toolCallId) {
        // PRE_TOOL hook
        HookResult hookResult = hooks.dispatch(Hook.Phase.PRE_TOOL,
            ToolUseEvent.preTool(toolName, arguments, ctx, toolCallId));
        if (hookResult instanceof HookResult.Block block) {
            return "Permission denied: " + block.reason();
        }

        ExecutableTool tool = find(toolName);
        if (tool == null) return "Error: Unknown tool: " + toolName;

        try {
            ToolOutput output = tool.execute(new ToolArgs(arguments), null);
            String resultText = switch (output) {
                case ToolOutput.Text t -> t.content();
                default -> output.toString();
            };

            // POST_TOOL hook
            hooks.dispatch(Hook.Phase.POST_TOOL,
                ToolUseEvent.postTool(toolName, arguments, ctx, toolCallId, ToolOutput.text(resultText)));

            return resultText;
        } catch (Exception e) {
            System.getLogger("ToolDispatcher").log(System.Logger.Level.WARNING,
                "Tool execution failed: " + toolName, e);
            return "Error executing " + toolName + ": " + e.getMessage();
        }
    }

    public void executeAll(List<InputItem.ToolCall> toolCalls, List<RunItem> newItems) {
        List<InputItem.ToolCall> readOnly = new ArrayList<>();
        List<InputItem.ToolCall> write = new ArrayList<>();
        for (InputItem.ToolCall tc : toolCalls) {
            (ToolClassifier.isReadOnly(tc.name()) ? readOnly : write).add(tc);
        }

        // Read-only in parallel
        if (readOnly.size() > 1) {
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                List<CompletableFuture<Void>> futures = readOnly.stream()
                    .map(tc -> CompletableFuture.runAsync(() -> executeAndRecord(tc, newItems), executor))
                    .toList();
                futures.forEach(CompletableFuture::join);
            }
        } else {
            readOnly.forEach(tc -> executeAndRecord(tc, newItems));
        }

        // Write sequential
        write.forEach(tc -> executeAndRecord(tc, newItems));
    }

    private void executeAndRecord(InputItem.ToolCall toolCall, List<RunItem> newItems) {
        ctx.output().printToolCall(toolCall.name(), toolCall.arguments());
        long startNanos = System.nanoTime();

        String resultText = executeTool(toolCall.name(), toolCall.arguments(), toolCall.id());

        if (resultText.startsWith("Permission denied")) {
            ctx.output().printPermissionDenied(toolCall.name());
        } else {
            ctx.output().printToolResult(toolCall.name(), resultText);
            ctx.output().printToolTiming(startNanos);
        }

        ctx.history().add(new InputItem.ToolResult(toolCall.id(), toolCall.name(), resultText));
        synchronized (newItems) {
            newItems.add(new RunItem.ToolCallItem("assistant", toolCall.id(), toolCall.name(), toolCall.arguments()));
            newItems.add(new RunItem.ToolOutputItem("assistant", toolCall.id(), toolCall.name(), ToolOutput.text(resultText)));
        }
    }

    public ExecutableTool find(String name) {
        return tools.stream().filter(t -> t.name().equals(name)).findFirst().orElse(null);
    }

    public List<ExecutableTool> all() { return Collections.unmodifiableList(tools); }

    public List<ToolDefinition> definitions() {
        return tools.stream().map(ExecutableTool::definition).toList();
    }
}
