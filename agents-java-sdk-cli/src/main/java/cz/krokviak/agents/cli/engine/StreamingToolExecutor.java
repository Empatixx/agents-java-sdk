package cz.krokviak.agents.cli.engine;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import cz.krokviak.agents.cli.CliContext;
import cz.krokviak.agents.cli.tool.ToolClassifier;
import cz.krokviak.agents.runner.InputItem;
import cz.krokviak.agents.runner.RunItem;
import cz.krokviak.agents.tool.ToolOutput;

import java.util.*;
import java.util.concurrent.*;

/**
 * Executes read-only tools eagerly during model streaming.
 * Write tools are deferred to collectResults().
 */
public class StreamingToolExecutor {

    private static final int TOOL_EXECUTION_TIMEOUT_SECONDS = 120;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ToolDispatcher toolDispatcher;
    private final CliContext ctx;
    private final Map<String, String> toolCallNames = new LinkedHashMap<>();
    private final Map<String, StringBuilder> toolCallArgs = new LinkedHashMap<>();
    private final Set<String> completedToolCalls = ConcurrentHashMap.newKeySet();
    private final Map<String, CompletableFuture<ToolResult>> pendingResults = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public record ToolResult(String toolCallId, String toolName, String resultText) {}

    public StreamingToolExecutor(ToolDispatcher toolDispatcher, CliContext ctx) {
        this.toolDispatcher = toolDispatcher;
        this.ctx = ctx;
    }

    public void onToolCallDelta(String id, String name, String argsDelta) {
        toolCallNames.putIfAbsent(id, name);
        toolCallArgs.computeIfAbsent(id, _ -> new StringBuilder()).append(argsDelta);
    }

    public void onToolCallComplete(String id) {
        if (!completedToolCalls.add(id)) return; // atomic check-and-add
        String name = toolCallNames.get(id);
        if (name != null && ToolClassifier.isReadOnly(name)) {
            pendingResults.put(id, CompletableFuture.supplyAsync(() -> executeWithHooks(id, name, parseArgs(id)), executor));
        }
    }

    public List<ToolResult> collectResults(List<InputItem.ToolCall> allToolCalls, List<RunItem> newItems) {
        List<ToolResult> results = new ArrayList<>();
        for (InputItem.ToolCall tc : allToolCalls) {
            ToolResult result;
            CompletableFuture<ToolResult> pending = pendingResults.get(tc.id());

            if (pending != null) {
                try {
                    result = pending.get(TOOL_EXECUTION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                } catch (Exception e) {
                    result = new ToolResult(tc.id(), tc.name(), "Error: tool execution timed out or failed: " + e.getMessage());
                }
                ctx.output().printToolCall(result.toolName(), tc.arguments());
                ctx.output().printToolResult(result.toolName(), result.resultText());
            } else {
                // Write tool — execute now
                ctx.output().printToolCall(tc.name(), tc.arguments());
                long start = System.nanoTime();
                result = executeWithHooks(tc.id(), tc.name(), tc.arguments());
                if (result.resultText().startsWith("Permission denied")) {
                    ctx.output().printPermissionDenied(tc.name());
                } else {
                    ctx.output().printToolResult(tc.name(), result.resultText());
                    ctx.output().printToolTiming(start);
                }
            }

            results.add(result);
            ctx.history().add(new InputItem.ToolResult(result.toolCallId(), result.toolName(), result.resultText()));
            synchronized (newItems) {
                newItems.add(new RunItem.ToolCallItem("assistant", tc.id(), tc.name(), tc.arguments()));
                newItems.add(new RunItem.ToolOutputItem("assistant", tc.id(), tc.name(), ToolOutput.text(result.resultText())));
            }
        }
        return results;
    }

    /** Delegates to ToolDispatcher.executeTool() to avoid duplicating hook + execution logic. */
    private ToolResult executeWithHooks(String id, String name, Map<String, Object> arguments) {
        String resultText = toolDispatcher.executeTool(name, arguments, id);
        return new ToolResult(id, name, resultText);
    }

    private Map<String, Object> parseArgs(String id) {
        StringBuilder argsJson = toolCallArgs.get(id);
        if (argsJson != null && !argsJson.isEmpty()) {
            try {
                return OBJECT_MAPPER.readValue(argsJson.toString(), new TypeReference<>() {});
            } catch (Exception e) {
                System.err.println("Warning: Failed to parse tool arguments for " + id + ": " + e.getMessage());
            }
        }
        return Map.of();
    }

    public void shutdown() {
        executor.close();
    }
}
