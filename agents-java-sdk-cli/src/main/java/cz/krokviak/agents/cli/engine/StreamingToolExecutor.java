package cz.krokviak.agents.cli.engine;

import cz.krokviak.agents.api.event.AgentEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import cz.krokviak.agents.cli.CliContext;
import cz.krokviak.agents.cli.render.ToolCallStatus;
import cz.krokviak.agents.agent.tool.ToolClassifier;
import cz.krokviak.agents.runner.InputItem;
import cz.krokviak.agents.runner.RunItem;
import cz.krokviak.agents.tool.ToolOutput;

import java.util.*;
import java.util.concurrent.*;

/**
 * Executes tools during model streaming.
 * <p>
 * Classification (via {@link ToolClassifier}):
 * <ul>
 *   <li><b>Concurrent-safe (read-only)</b> — launched immediately on a virtual-thread
 *       as soon as the tool call is complete during streaming.</li>
 *   <li><b>Exclusive (write)</b> — deferred and executed one at a time in
 *       {@link #collectResults} to avoid interleaved side-effects.</li>
 * </ul>
 * Results are always returned in tool-receipt order regardless of completion order.
 */
public class StreamingToolExecutor {
    private static final Logger log = LoggerFactory.getLogger(StreamingToolExecutor.class);

    private static final int TOOL_EXECUTION_TIMEOUT_SECONDS = cz.krokviak.agents.cli.CliDefaults.TOOL_EXECUTION_TIMEOUT_SECONDS;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ToolDispatcher toolDispatcher;
    private final CliContext ctx;
    /** Insertion-ordered and thread-safe for concurrent streaming + collection. */
    private final Map<String, String> toolCallNames = java.util.Collections.synchronizedMap(new LinkedHashMap<>());
    private final Map<String, StringBuilder> toolCallArgs = java.util.Collections.synchronizedMap(new LinkedHashMap<>());
    private final Set<String> completedToolCalls = ConcurrentHashMap.newKeySet();
    /** Futures for concurrent-safe (read-only) tool calls, keyed by tool-call id. */
    private final Map<String, CompletableFuture<ToolResult>> pendingResults = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public record ToolResult(String toolCallId, String toolName, String resultText) {}

    public StreamingToolExecutor(ToolDispatcher toolDispatcher, CliContext ctx) {
        this.toolDispatcher = toolDispatcher;
        this.ctx = ctx;
    }

    public void onToolCallDelta(String id, String name, String argsDelta) {
        if (id == null || argsDelta == null) return;
        toolCallNames.putIfAbsent(id, name != null ? name : "");
        toolCallArgs.computeIfAbsent(id, _ -> new StringBuilder()).append(argsDelta);
    }

    /**
     * Called when a tool-call delta stream is complete. Concurrent-safe tools are
     * dispatched immediately on a virtual thread; exclusive tools wait until
     * {@link #collectResults}.
     */
    public void onToolCallComplete(String id) {
        if (id == null) return;
        if (!completedToolCalls.add(id)) return; // atomic check-and-add
        String name = toolCallNames.get(id);
        if (name != null && ToolClassifier.isReadOnly(name)) {
            Map<String, Object> args = parseArgs(id);
            ctx.eventBus().emit(new cz.krokviak.agents.api.event.AgentEvent.ToolStarted(name, args, id, false));
            pendingResults.put(id, CompletableFuture.supplyAsync(
                () -> executeWithHooks(id, name, args), executor));
        }
    }

    /**
     * Collect all tool results in receipt order. Concurrent-safe results are awaited
     * (they may already be done); exclusive tools are executed serially here.
     */
    public List<ToolResult> collectResults(List<InputItem.ToolCall> allToolCalls, List<RunItem> newItems) {
        // Show overall progress if there are multiple tools
        int total = allToolCalls.size();
        List<ToolResult> results = new ArrayList<>();

        for (int i = 0; i < allToolCalls.size(); i++) {
            InputItem.ToolCall tc = allToolCalls.get(i);
            ToolResult result;
            CompletableFuture<ToolResult> pending = pendingResults.get(tc.id());

            var bus = ctx.eventBus();
            if (pending != null) {
                // Concurrent-safe: was already running; just await result
                try {
                    result = pending.get(TOOL_EXECUTION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                } catch (Exception e) {
                    result = new ToolResult(tc.id(), tc.name(),
                        "Error: tool execution timed out or failed: " + e.getMessage());
                }
                int lines = result.resultText().split("\n", -1).length;
                bus.emit(new cz.krokviak.agents.api.event.AgentEvent.ToolCompleted(
                    result.toolName(), result.resultText(), lines, 0));
            } else {
                // Exclusive: execute now, serially
                bus.emit(new cz.krokviak.agents.api.event.AgentEvent.ToolStarted(
                    tc.name(), tc.arguments(), tc.id(), false));
                long start = System.nanoTime();
                result = executeWithHooks(tc.id(), tc.name(), tc.arguments());
                long ms = (System.nanoTime() - start) / 1_000_000;
                if (result.resultText().startsWith("Permission denied")) {
                    bus.emit(new cz.krokviak.agents.api.event.AgentEvent.ToolBlocked(tc.name(), result.resultText()));
                } else {
                    int lines = result.resultText().split("\n", -1).length;
                    bus.emit(new cz.krokviak.agents.api.event.AgentEvent.ToolCompleted(
                        tc.name(), result.resultText(), lines, ms));
                }
            }

            results.add(result);
            ctx.history().add(new InputItem.ToolResult(result.toolCallId(), result.toolName(), result.resultText()));
            synchronized (newItems) {
                newItems.add(new RunItem.ToolCallItem("assistant", tc.id(), tc.name(), tc.arguments()));
                newItems.add(new RunItem.ToolOutputItem("assistant", tc.id(), tc.name(), ToolOutput.text(result.resultText())));
            }
        }

        // progress tracking removed — tool calls shown individually
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
                log.warn(
                    "Failed to parse tool arguments for " + id + ": " + e.getMessage());
            }
        }
        return Map.of();
    }

    public void shutdown() {
        executor.close();
    }
}
