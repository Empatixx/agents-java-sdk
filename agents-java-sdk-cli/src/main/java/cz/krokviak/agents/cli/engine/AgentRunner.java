package cz.krokviak.agents.cli.engine;

import cz.krokviak.agents.cli.CliContext;
import cz.krokviak.agents.cli.mailbox.MailboxManager;
import cz.krokviak.agents.cli.task.TaskManager;
import cz.krokviak.agents.exception.ContextTooLongException;
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
        this.tokenBudget = new TokenBudget(cz.krokviak.agents.cli.CliDefaults.TOKEN_BUDGET);
    }

    public void run(String userInput) {
        ctx.history().add(new InputItem.UserMessage(userInput));
        List<RunItem> newItems = new ArrayList<>();
        newItems.add(new RunItem.UserInput(userInput));

        try {
            boolean retried = false;
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
                    if (planPath == null) planPath = "~/.krok/plans/<plan>.md";
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
                } catch (ContextTooLongException e) {
                    if (firstEvent) ctx.output().stopSpinner();
                    streamingExecutor.shutdown();
                    if (retried) {
                        ctx.output().printError("Context still too large after compaction. Try /compact manually.");
                        break;
                    }
                    retried = true;
                    ctx.output().println("\033[2m[Context too large — compacting and retrying]\033[0m");
                    var reactiveCompacted = ctx.compactionPipeline().reactiveCompact(ctx.history(), ctx.systemPrompt());
                    ctx.history().clear();
                    ctx.history().addAll(reactiveCompacted);
                    turn--; // retry this turn
                    continue;
                }
                retried = false;
                if (firstEvent) ctx.output().stopSpinner();
                if (collector.text() != null) System.out.println();

                // Track cost + token budget
                ModelResponse resp = collector.response();
                if (resp != null && resp.usage() != null) {
                    ctx.costTracker().record(ctx.modelId(), resp.usage().inputTokens(), resp.usage().outputTokens());
                    tokenBudget.recordTurn(resp.usage().inputTokens(), resp.usage().outputTokens());
                    int charCount = cz.krokviak.agents.cli.context.TokenEstimator.countChars(ctx.history());
                    ctx.tokenEstimator().calibrate(resp.usage().inputTokens(), charCount);
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

                // Token budget check — ask user to extend
                if (tokenBudget.isOverBudget()) {
                    if (!promptExtendBudget()) break;
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
            try {
                ctx.session().save(ctx.sessionId(), newItems);
                updateSessionMetadata();
            } catch (Exception e) {
                ctx.output().printError("Failed to save session: " + e.getMessage());
            }
        }
    }

    private boolean promptExtendBudget() {
        int newBudget = tokenBudget.maxBudget() * 2;
        String header = String.format("Token budget exceeded %s. Increase to %,d?", tokenBudget.format(), newBudget);
        String[] options = {"Yes, increase 2x", "No, stop"};

        var renderer = ctx.tuiRenderer();
        int selected;
        if (renderer != null) {
            selected = renderer.promptPermission(header, options);
        } else {
            ctx.output().println(header);
            ctx.output().println("  1. " + options[0]);
            ctx.output().println("  2. " + options[1]);
            try {
                int c = System.in.read();
                while (System.in.available() > 0) System.in.read();
                selected = (c == '1' || c == 'y') ? 0 : 1;
            } catch (Exception e) { selected = 1; }
        }

        if (selected == 0) {
            tokenBudget.extend(2);
            ctx.output().println("Budget increased to " + String.format("%,d", tokenBudget.maxBudget()) + " tokens.");
            return true;
        }
        ctx.output().println("Stopping.");
        return false;
    }

    private void updateSessionMetadata() {
        var advanced = ctx.advancedSession();
        if (advanced == null) return;
        try {
            String title = null;
            for (var item : ctx.history()) {
                if (item instanceof InputItem.UserMessage msg) {
                    String content = msg.content();
                    title = content.length() > 80 ? content.substring(0, 80) + "..." : content;
                    break;
                }
            }
            advanced.saveMetadata(new cz.krokviak.agents.session.SessionMetadata(
                ctx.sessionId(), title, java.time.Instant.now(), java.time.Instant.now(),
                ctx.history().size(), ctx.workingDirectory().toAbsolutePath().toString()));
        } catch (Exception e) {
            System.getLogger("AgentRunner").log(System.Logger.Level.WARNING, "Failed to update session metadata", e);
        }
    }
}
