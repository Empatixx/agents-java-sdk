package cz.krokviak.agents.cli.engine;

import cz.krokviak.agents.cli.CliContext;
import cz.krokviak.agents.cli.mailbox.MailboxManager;
import cz.krokviak.agents.cli.task.TaskManager;
import cz.krokviak.agents.model.*;
import cz.krokviak.agents.runner.InputItem;
import cz.krokviak.agents.runner.RunItem;

import java.util.ArrayList;
import java.util.List;

public class AgentRunner {
    private final CliContext ctx;
    private final ToolDispatcher toolDispatcher;
    private final int maxTurns;
    private final TokenBudget tokenBudget;

    public AgentRunner(CliContext ctx, ToolDispatcher toolDispatcher, int maxTurns) {
        this.ctx = ctx;
        this.toolDispatcher = toolDispatcher;
        this.maxTurns = maxTurns;
        this.tokenBudget = new TokenBudget(200_000);
    }

    public void run(String userInput) {
        ctx.history().add(new InputItem.UserMessage(userInput));
        List<RunItem> newItems = new ArrayList<>();

        try {
            for (int turn = 0; turn < maxTurns; turn++) {
                // Inject pending task notifications
                injectTaskNotifications();
                injectMailboxMessages();

                // 3-layer compaction pipeline
                var compacted = ctx.compactionPipeline().compact(ctx.history(), ctx.systemPrompt());
                if (compacted != ctx.history()) {
                    ctx.history().clear();
                    ctx.history().addAll(compacted);
                }

                // Inject plan mode instructions into system prompt if active
                String systemPrompt = ctx.systemPrompt();
                if (ctx.isPlanMode() && ctx.planStore() != null) {
                    String planPath = ctx.planStore().currentPlanPath();
                    if (planPath == null) planPath = "~/.claude/plans/<plan>.md";
                    systemPrompt += "\n\n" + cz.krokviak.agents.cli.plan.PlanPrompts.planModeInstructions(planPath);
                }

                LlmContext llmCtx = new LlmContext(systemPrompt, List.copyOf(ctx.history()),
                    toolDispatcher.definitions(), null, ctx.modelSettings());

                // Streaming with eager tool execution
                StreamingToolExecutor streamingExecutor = new StreamingToolExecutor(toolDispatcher, ctx);
                StreamCollector collector = new StreamCollector();
                ctx.output().startSpinner("Thinking...");
                boolean firstEvent = true;

                try (ModelResponseStream stream = ctx.model().callStreamed(llmCtx, ctx.modelSettings())) {
                    for (ModelResponseStream.Event event : stream) {
                        if (firstEvent) { ctx.output().stopSpinner(); firstEvent = false; }
                        switch (event) {
                            case ModelResponseStream.Event.TextDelta td -> {
                                ctx.output().printTextDelta(td.delta());
                                collector.onTextDelta(td.delta());
                            }
                            case ModelResponseStream.Event.ToolCallDelta tcd -> {
                                collector.onToolCallDelta(tcd.toolCallId(), tcd.name(), tcd.argumentsDelta());
                                // Feed to streaming executor for eager execution
                                streamingExecutor.onToolCallDelta(tcd.toolCallId(), tcd.name(), tcd.argumentsDelta());
                            }
                            case ModelResponseStream.Event.Done done -> {
                                collector.onDone(done.fullResponse());
                                // Mark all tool calls as complete for streaming executor
                                for (var tc : collector.toolCalls()) {
                                    streamingExecutor.onToolCallComplete(tc.id());
                                }
                            }
                        }
                    }
                }
                if (firstEvent) ctx.output().stopSpinner();
                if (collector.text() != null) System.out.println();

                // Track cost + token budget
                ModelResponse resp = collector.response();
                if (resp != null && resp.usage() != null) {
                    ctx.costTracker().record(ctx.modelId(), resp.usage().inputTokens(), resp.usage().outputTokens());
                    tokenBudget.recordTurn(resp.usage().inputTokens(), resp.usage().outputTokens());
                }

                // Update history
                List<InputItem.ToolCall> toolCalls = collector.toolCalls();
                ctx.history().add(new InputItem.AssistantMessage(collector.text(), toolCalls));
                if (collector.text() != null) {
                    newItems.add(new RunItem.MessageOutput("assistant", collector.text()));
                }

                if (!collector.hasToolCalls()) {
                    saveSession(newItems);
                    streamingExecutor.shutdown();
                    return;
                }

                // Collect results from streaming executor (read-only already running, write tools execute now)
                streamingExecutor.collectResults(toolCalls, newItems);
                streamingExecutor.shutdown();

                // Token budget check
                if (tokenBudget.isOverBudget()) {
                    ctx.output().printError("Token budget exceeded " + tokenBudget.format());
                    break;
                }
                if (tokenBudget.isDiminishingReturns()) {
                    ctx.output().println("\033[2m[Diminishing returns detected — stopping]\033[0m");
                    break;
                }
            }

            if (!tokenBudget.isOverBudget() && !tokenBudget.isDiminishingReturns()) {
                ctx.output().printError("Reached maximum turns (" + maxTurns + ")");
            }
        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg == null) msg = e.getClass().getSimpleName();
            ctx.output().printError(msg);
            e.printStackTrace(System.err);
        }

        saveSession(newItems);
    }

    private void injectTaskNotifications() {
        TaskManager tm = ctx.taskManager();
        if (tm == null) return;
        var notifications = tm.drainNotifications();
        for (var n : notifications) {
            String msg = String.format("<task-notification>\n  task-id: %s\n  status: %s\n  description: %s\n  summary: %s\n</task-notification>",
                n.taskId(), n.status(), n.description(), n.summary());
            ctx.history().add(new InputItem.SystemMessage(msg));
            ctx.output().println("\033[2m[Task " + n.taskId() + " " + n.status() + ": " + n.description() + "]\033[0m");
        }
    }

    private void injectMailboxMessages() {
        MailboxManager mailboxManager = ctx.mailboxManager();
        if (mailboxManager == null) return;

        for (var message : mailboxManager.drain("main")) {
            String msg = String.format("<mailbox-message>\n  from: %s\n  to: %s\n  content: %s\n</mailbox-message>",
                message.sender(), message.recipient(), message.content());
            ctx.history().add(new InputItem.SystemMessage(msg));
            ctx.output().println("\033[2m[Message from " + message.sender() + "]\033[0m " + message.content());
        }
    }

    private void saveSession(List<RunItem> newItems) {
        if (ctx.session() != null && ctx.sessionId() != null && !newItems.isEmpty()) {
            try { ctx.session().save(ctx.sessionId(), newItems); }
            catch (Exception e) { ctx.output().printError("Failed to save session: " + e.getMessage()); }
        }
    }
}
