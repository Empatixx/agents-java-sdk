package cz.krokviak.agents.agent.spawn;

import cz.krokviak.agents.agent.spawn.AgentRegistry;
import cz.krokviak.agents.agent.spawn.RunningAgent;
import cz.krokviak.agents.agent.spawn.ProgressTracker;
import cz.krokviak.agents.agent.spawn.AgentStatus;

import cz.krokviak.agents.api.event.AgentEvent;

import cz.krokviak.agents.agent.AgentContext;
import cz.krokviak.agents.agent.mailbox.MailboxManager;
import cz.krokviak.agents.agent.spawn.AgentStatus;
import cz.krokviak.agents.agent.task.TaskManager;
import cz.krokviak.agents.agent.task.TaskState;
import cz.krokviak.agents.model.*;
import cz.krokviak.agents.runner.InputItem;
import cz.krokviak.agents.tool.ExecutableTool;
import cz.krokviak.agents.tool.ToolArgs;
import cz.krokviak.agents.tool.ToolDefinition;
import cz.krokviak.agents.tool.ToolOutput;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AgentSpawner {

    private static final String AGENT_BASE_PROMPT = """
        You are a sub-agent spawned to handle a specific task. You have access to file system tools. \
        Complete your task thoroughly and return clear, structured results. Be concise but complete.""";

    private static final long SUMMARY_INTERVAL_SECS = 30;

    private final AgentContext ctx;
    private final AgentRegistry registry;
    private final TaskManager taskManager;
    // Non-daemon executor with explicit shutdown() — JVM SIGTERM or SESSION_END
    // drains pending summary calls instead of swallowing them mid-I/O.
    private final java.util.concurrent.ScheduledExecutorService summaryScheduler =
        java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "agent-summary-scheduler");
            t.setDaemon(false);
            return t;
        });

    public AgentSpawner(AgentContext ctx, AgentRegistry registry, TaskManager taskManager) {
        this.ctx = ctx;
        this.registry = registry;
        this.taskManager = taskManager;
    }

    /**
     * Graceful shutdown — stops accepting new summary ticks and drains the in-flight
     * ones with a 5-second budget. Should be called from the SESSION_END hook (CLI.java
     * wires a JVM shutdown hook for this).
     */
    public void shutdown() {
        summaryScheduler.shutdown();
        try {
            if (!summaryScheduler.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                summaryScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            summaryScheduler.shutdownNow();
        }
    }

    /**
     * Run agent synchronously, return output string.
     */
    public String spawnForeground(String name, String prompt, List<ExecutableTool> tools,
                                  Model model, ProgressTracker progress, int maxTurns) {
        RunningAgent agent = new RunningAgent(name, name, false);
        agent.setStatus(AgentStatus.RUNNING);
        registry.register(name, agent);

        ctx.eventBus().emit(new cz.krokviak.agents.api.event.AgentEvent.AgentStarted(name, name));
        fireSubagentStart(name, false);

        try {
            String result = runLoop(name, prompt, tools, model, progress, agent, maxTurns);
            agent.setStatus(AgentStatus.COMPLETED);
            ctx.eventBus().emit(new cz.krokviak.agents.api.event.AgentEvent.AgentCompleted(name,
                cz.krokviak.agents.util.StringUtils.truncate(result, 200, "")));
            fireSubagentStop(name, AgentStatus.COMPLETED, result, false);
            registry.remove(name);
            return result;
        } catch (Exception e) {
            agent.setStatus(AgentStatus.FAILED);
            ctx.eventBus().emit(new cz.krokviak.agents.api.event.AgentEvent.AgentFailed(name, e.getMessage()));
            fireSubagentStop(name, AgentStatus.FAILED, e.getMessage(), false);
            registry.remove(name);
            return "Error: " + e.getMessage();
        }
    }

    /**
     * Spawn agent on a virtual thread, returns RunningAgent immediately.
     */
    public RunningAgent spawnBackground(String name, String prompt, List<ExecutableTool> tools,
                                        Model model, ProgressTracker progress, int maxTurns) {
        RunningAgent agent = new RunningAgent(name, name, true);
        registry.register(name, agent);

        String taskId = taskManager.nextId();
        TaskState task = new TaskState(taskId, name);
        taskManager.register(task);

        ctx.eventBus().emit(new cz.krokviak.agents.api.event.AgentEvent.AgentStarted(name, "background"));
        fireSubagentStart(name, true);

        java.util.concurrent.ScheduledFuture<?> summaryTask = schedulePeriodicSummary(name, agent);

        Thread thread = Thread.startVirtualThread(() -> {
            agent.setStatus(AgentStatus.RUNNING);
            task.start();
            try {
                String result = runLoop(name, prompt, tools, model, progress, agent, maxTurns);
                agent.appendOutput(result);
                agent.setStatus(AgentStatus.COMPLETED);
                task.complete(result);
                taskManager.addNotification(new TaskManager.TaskNotification(
                    taskId, name, TaskState.Status.COMPLETED,
                    cz.krokviak.agents.util.StringUtils.truncate(result, 200)));
                fireSubagentStop(name, AgentStatus.COMPLETED, result, true);
            } catch (Exception e) {
                agent.setStatus(AgentStatus.FAILED);
                task.fail(e.getMessage());
                taskManager.addNotification(new TaskManager.TaskNotification(
                    taskId, name, TaskState.Status.FAILED, e.getMessage()));
                fireSubagentStop(name, AgentStatus.FAILED, e.getMessage(), true);
            } finally {
                summaryTask.cancel(false);
            }
        });

        agent.setThreadRef(thread);
        task.setThread(thread);
        return agent;
    }

    /**
     * Spawn agent isolated in a git worktree directory.
     */
    public RunningAgent spawnIsolated(String name, String prompt, List<ExecutableTool> tools,
                                      Model model, ProgressTracker progress, String worktreePath) {
        ctx.eventBus().emit(new cz.krokviak.agents.api.event.AgentEvent.AgentStarted(name, "isolated:" + worktreePath));
        return spawnBackground(name, prompt, tools, model, progress, 15);
    }

    private String runLoop(String agentId, String prompt, List<ExecutableTool> tools,
                           Model model, ProgressTracker progress, RunningAgent agent, int maxTurns) {
        // Tag this thread so TuiRenderer knows which agent's tools to group
        // AgentStarted event already emitted — renderer handles setCurrentAgent via event
        try {
        List<ToolDefinition> defs = tools.stream()
            .filter(t -> !t.name().equals("agent"))
            .map(ExecutableTool::definition)
            .toList();

        // System prompt: same as main worker (AGENTS.md, memory, project instructions) + agent role
        String systemPrompt = ctx.systemPrompt() + "\n\n" + AGENT_BASE_PROMPT
            + "\nWorking directory: " + ctx.workingDirectory().toAbsolutePath();

        // History: main worker's user/assistant messages as context (skip tool calls/results — they'd break API pairing)
        List<InputItem> history = new ArrayList<>();
        for (var item : ctx.history()) {
            switch (item) {
                case InputItem.UserMessage m -> history.add(m);
                case InputItem.AssistantMessage m -> {
                    // Keep text content, strip tool calls
                    if (m.content() != null && !m.content().isBlank()) {
                        history.add(new InputItem.AssistantMessage(m.content()));
                    }
                }
                case InputItem.SystemMessage m -> history.add(m);
                case InputItem.CompactionMarker m -> history.add(new InputItem.SystemMessage(m.summary()));
                default -> {} // skip ToolResult, ImageContent — would break API pairing
            }
        }
        history.add(new InputItem.UserMessage(prompt));
        StringBuilder response = new StringBuilder();

        for (int turn = 0; turn < maxTurns; turn++) {
            ctx.abortSignal().throwIfAborted();
            injectMailbox(history, agentId);
            if (progress != null) progress.updateActivity("turn " + (turn + 1));

            // Streaming sub-agent: same architecture as main AgentRunner. Text/thinking/
            // tool-call deltas flow onto the shared event bus so TUI + dashboards see
            // sub-agents live, not just their final result.
            var bus = ctx.eventBus();
            var collector = new cz.krokviak.agents.agent.engine.StreamCollector();
            var settings = ModelSettings.builder().maxTokens(8192).build();
            var llmCtx = new LlmContext(systemPrompt, List.copyOf(history), defs, null, settings);

            try (cz.krokviak.agents.model.ModelResponseStream stream = model.callStreamed(llmCtx, settings)) {
                cz.krokviak.agents.agent.engine.TurnStreamPump.pump(stream, bus, collector);
            }

            ModelResponse resp = collector.response();
            if (resp != null && resp.usage() != null) {
                int tokens = resp.usage().inputTokens() + resp.usage().outputTokens();
                if (progress != null) progress.addTokens(tokens);
                agent.addTokens(tokens);
            }

            if (collector.text() != null) response.append(collector.text());
            List<InputItem.ToolCall> tcs = collector.toolCalls();
            boolean hasTools = !tcs.isEmpty();

            if (!hasTools) break;

            history.add(new InputItem.AssistantMessage(response.isEmpty() ? null : response.toString(), tcs));
            response.setLength(0);

            for (var tc : tcs) {
                agent.setCurrentActivity(tc.name() + "(" + tc.arguments() + ")");
                bus.emit(new cz.krokviak.agents.api.event.AgentEvent.ToolStarted(
                    tc.name(), tc.arguments(), tc.id(), true));
                long startNanos = System.nanoTime();

                ExecutableTool tool = tools.stream()
                    .filter(t -> t.name().equals(tc.name()) && !t.name().equals("agent"))
                    .findFirst().orElse(null);
                String result = tool != null ? executeSafe(tool, tc.arguments()) : "Error: unknown tool " + tc.name();
                history.add(new InputItem.ToolResult(tc.id(), tc.name(), result));

                long ms = (System.nanoTime() - startNanos) / 1_000_000;
                bus.emit(new cz.krokviak.agents.api.event.AgentEvent.ToolCompleted(
                    tc.name(), result, result.split("\n", -1).length, ms));

                if (progress != null) progress.addToolUse();
                agent.addToolUse();
                if (progress != null) {
                    bus.emit(new cz.krokviak.agents.api.event.AgentEvent.AgentProgress(
                        agentId, progress.getProgressLine()));
                }
            }
        }

        return response.isEmpty() ? "(no response)" : response.toString();
        } finally {
            // AgentCompleted/Failed event handles clearCurrentAgent via listener
        }
    }

    private void fireSubagentStart(String name, boolean background) {
        var hooks = ctx.hooks();
        if (hooks == null) return;
        try {
            hooks.dispatchTyped(cz.krokviak.agents.api.hook.HookPhase.SUBAGENT_START,
                new cz.krokviak.agents.api.hook.events.SubagentEvent(name, name, "STARTING", null, background));
        } catch (Exception ignored) {}
    }

    private void fireSubagentStop(String name, AgentStatus status, String result, boolean background) {
        var hooks = ctx.hooks();
        if (hooks == null) return;
        try {
            String truncated = result == null ? null :
                (result.length() > 500 ? result.substring(0, 500) + "..." : result);
            hooks.dispatchTyped(cz.krokviak.agents.api.hook.HookPhase.SUBAGENT_STOP,
                new cz.krokviak.agents.api.hook.events.SubagentEvent(name, name, status.name(), truncated, background));
        } catch (Exception ignored) {}
    }

    private static final String AGENT_PROGRESS_SYSTEM_PROMPT = """
        Describe the sub-agent's most recent action in 3–5 words using present tense (-ing).
        Name the file or function, not a branch or category. Output the sentence only,
        no quotes, no trailing punctuation.
        Good: Reading runAgent.ts
        Good: Fixing null check in validate.ts
        Good: Running auth module tests
        Bad (past tense): Analyzed the branch diff
        Bad (too vague): Investigating the issue""";

    /** Schedule a periodic {@code AgentProgress} summary tick for a running background worker. */
    private java.util.concurrent.ScheduledFuture<?> schedulePeriodicSummary(String agentId, RunningAgent agent) {
        final java.util.concurrent.atomic.AtomicReference<String> prevSummary = new java.util.concurrent.atomic.AtomicReference<>();
        return summaryScheduler.scheduleAtFixedRate(() -> {
            try {
                if (agent.status() != AgentStatus.RUNNING) return;
                if (ctx.summaryModelOrMain() == null) return;
                String activity = agent.currentActivity();
                if (activity == null || activity.isBlank()) return;
                String user = "Most recent activity: " + activity
                    + "\nTool calls so far: " + agent.toolUseCount()
                    + "\nElapsed: " + agent.elapsed();
                String prev = prevSummary.get();
                if (prev != null) user += "\nPrevious summary: \"" + prev + "\" — say something NEW.";
                var svc = new cz.krokviak.agents.agent.summary.SummaryService(ctx);
                svc.summarize(AGENT_PROGRESS_SYSTEM_PROMPT, user, 64).ifPresent(summary -> {
                    prevSummary.set(summary);
                    ctx.eventBus().emit(new cz.krokviak.agents.api.event.AgentEvent.AgentProgress(agentId, summary));
                });
            } catch (Exception ignored) {
                // Never propagate; a flaky summary call must not crash the scheduler.
            }
        }, SUMMARY_INTERVAL_SECS, SUMMARY_INTERVAL_SECS, java.util.concurrent.TimeUnit.SECONDS);
    }

    private void injectMailbox(List<InputItem> history, String agentId) {
        MailboxManager mailboxManager = ctx.mailboxManager();
        if (mailboxManager == null) return;
        for (var message : mailboxManager.drain(agentId)) {
            String content = String.format("<mailbox-message>\n  from: %s\n  to: %s\n  content: %s\n</mailbox-message>",
                message.sender(), message.recipient(), message.content());
            history.add(new InputItem.SystemMessage(content));
        }
    }

    private String executeSafe(ExecutableTool tool, Map<String, Object> args) {
        try {
            ToolOutput o = tool.execute(new ToolArgs(args), null);
            return switch (o) {
                case ToolOutput.Text t -> t.content();
                default -> o.toString();
            };
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

}
