package cz.krokviak.agents.cli.agent;

import cz.krokviak.agents.cli.CliContext;
import cz.krokviak.agents.cli.mailbox.MailboxManager;
import cz.krokviak.agents.cli.render.AgentStatus;
import cz.krokviak.agents.cli.task.TaskManager;
import cz.krokviak.agents.cli.task.TaskState;
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

    private final CliContext ctx;
    private final AgentRegistry registry;
    private final TaskManager taskManager;

    public AgentSpawner(CliContext ctx, AgentRegistry registry, TaskManager taskManager) {
        this.ctx = ctx;
        this.registry = registry;
        this.taskManager = taskManager;
    }

    /**
     * Run agent synchronously, return output string.
     */
    public String spawnForeground(String name, String prompt, List<ExecutableTool> tools,
                                  Model model, ProgressTracker progress, int maxTurns) {
        RunningAgent agent = new RunningAgent(name, name, false);
        agent.setStatus(AgentStatus.RUNNING);
        registry.register(name, agent);

        ctx.eventBus().emit(new cz.krokviak.agents.cli.event.CliEvent.AgentStarted(name, name));

        try {
            String result = runLoop(name, prompt, tools, model, progress, agent, maxTurns);
            agent.setStatus(AgentStatus.COMPLETED);
            ctx.eventBus().emit(new cz.krokviak.agents.cli.event.CliEvent.AgentCompleted(name, result.length() > 200 ? result.substring(0, 200) : result));
            registry.remove(name);
            return result;
        } catch (Exception e) {
            agent.setStatus(AgentStatus.FAILED);
            ctx.eventBus().emit(new cz.krokviak.agents.cli.event.CliEvent.AgentFailed(name, e.getMessage()));
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

        ctx.eventBus().emit(new cz.krokviak.agents.cli.event.CliEvent.AgentStarted(name, "background"));

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
                    result.length() > 200 ? result.substring(0, 200) + "..." : result));
            } catch (Exception e) {
                agent.setStatus(AgentStatus.FAILED);
                task.fail(e.getMessage());
                taskManager.addNotification(new TaskManager.TaskNotification(
                    taskId, name, TaskState.Status.FAILED, e.getMessage()));
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
        ctx.eventBus().emit(new cz.krokviak.agents.cli.event.CliEvent.AgentStarted(name, "isolated:" + worktreePath));
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
            injectMailbox(history, agentId);
            if (progress != null) progress.updateActivity("turn " + (turn + 1));

            ModelResponse resp = model.call(
                new LlmContext(systemPrompt, List.copyOf(history), defs, null,
                    ModelSettings.builder().maxTokens(8192).build()),
                ModelSettings.builder().maxTokens(8192).build());

            // Track tokens
            if (resp.usage() != null) {
                int tokens = resp.usage().inputTokens() + resp.usage().outputTokens();
                if (progress != null) progress.addTokens(tokens);
                agent.addTokens(tokens);
            }

            boolean hasTools = false;
            List<InputItem.ToolCall> tcs = new ArrayList<>();
            for (var out : resp.output()) {
                switch (out) {
                    case ModelResponse.OutputItem.Message msg -> response.append(msg.content());
                    case ModelResponse.OutputItem.ToolCallRequest tc -> {
                        hasTools = true;
                        tcs.add(new InputItem.ToolCall(tc.id(), tc.name(), tc.arguments()));
                    }
                }
            }

            if (!hasTools) break;

            history.add(new InputItem.AssistantMessage(response.isEmpty() ? null : response.toString(), tcs));
            response.setLength(0);

            var bus = ctx.eventBus();
            for (var tc : tcs) {
                bus.emit(new cz.krokviak.agents.cli.event.CliEvent.ToolStarted(
                    tc.name(), tc.arguments(), tc.id(), true));
                long startNanos = System.nanoTime();

                ExecutableTool tool = tools.stream()
                    .filter(t -> t.name().equals(tc.name()) && !t.name().equals("agent"))
                    .findFirst().orElse(null);
                String result = tool != null ? executeSafe(tool, tc.arguments()) : "Error: unknown tool " + tc.name();
                history.add(new InputItem.ToolResult(tc.id(), tc.name(), result));

                long ms = (System.nanoTime() - startNanos) / 1_000_000;
                bus.emit(new cz.krokviak.agents.cli.event.CliEvent.ToolCompleted(
                    tc.name(), result, result.split("\n", -1).length, ms));

                if (progress != null) progress.addToolUse();
                agent.addToolUse();
                if (progress != null) {
                    bus.emit(new cz.krokviak.agents.cli.event.CliEvent.AgentProgress(
                        agentId, progress.getProgressLine()));
                }
            }
        }

        return response.isEmpty() ? "(no response)" : response.toString();
        } finally {
            // AgentCompleted/Failed event handles clearCurrentAgent via listener
        }
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
