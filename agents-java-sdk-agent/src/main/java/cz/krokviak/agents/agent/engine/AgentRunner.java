package cz.krokviak.agents.agent.engine;

import cz.krokviak.agents.agent.engine.TokenBudget;

import cz.krokviak.agents.api.event.AgentEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cz.krokviak.agents.agent.AgentContext;
import cz.krokviak.agents.agent.mailbox.MailboxManager;
import cz.krokviak.agents.agent.task.TaskManager;
import cz.krokviak.agents.exception.ContextTooLongException;
import cz.krokviak.agents.model.*;
import cz.krokviak.agents.runner.InputItem;
import cz.krokviak.agents.runner.RunItem;

import java.util.ArrayList;
import java.util.List;

public class AgentRunner {
    private static final Logger log = LoggerFactory.getLogger(AgentRunner.class);
    private final AgentContext ctx;
    private final ToolDispatcher toolDispatcher;
    private final int maxTurns;
    private final TokenBudget tokenBudget;

    public AgentRunner(AgentContext ctx, ToolDispatcher toolDispatcher, int maxTurns) {
        this.ctx = ctx;
        this.toolDispatcher = toolDispatcher;
        this.maxTurns = maxTurns;
        this.tokenBudget = new TokenBudget(cz.krokviak.agents.agent.AgentDefaults.TOKEN_BUDGET);
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

                // Build system prompt: base + frontend-injected suffix (e.g. output style) + optional plan-mode block
                String systemPrompt = ctx.effectiveSystemPrompt();
                if (ctx.isPlanMode() && ctx.planStore() != null) {
                    String planPath = ctx.planStore().currentPlanPath();
                    if (planPath == null) planPath = "~/.krok/plans/<plan>.md";
                    systemPrompt += "\n\n" + cz.krokviak.agents.agent.plan.PlanPrompts.planModeInstructions(planPath);
                }

                LlmContext llmCtx = new LlmContext(systemPrompt, List.copyOf(ctx.history()),
                    toolDispatcher.definitions(), null, ctx.modelSettings());

                // Streaming with eager tool execution
                StreamingToolExecutor streamingExecutor = new StreamingToolExecutor(toolDispatcher, ctx);
                StreamCollector collector = new StreamCollector();
                var bus = ctx.eventBus();
                bus.emit(new cz.krokviak.agents.api.event.AgentEvent.SpinnerStart("Thinking..."));
                boolean firstEvent = true;

                boolean thinkingActive = false;
                try (ModelResponseStream stream = ctx.model().callStreamed(llmCtx, ctx.modelSettings())) {
                    for (ModelResponseStream.Event event : stream) {
                        if (firstEvent) { bus.emit(new cz.krokviak.agents.api.event.AgentEvent.SpinnerStop()); firstEvent = false; }
                        switch (event) {
                            case ModelResponseStream.Event.TextDelta td -> {
                                if (thinkingActive) {
                                    bus.emit(new cz.krokviak.agents.api.event.AgentEvent.ThinkingDone());
                                    thinkingActive = false;
                                }
                                bus.emit(new cz.krokviak.agents.api.event.AgentEvent.ResponseDelta(td.delta()));
                                collector.onTextDelta(td.delta());
                            }
                            case ModelResponseStream.Event.ThinkingDelta thd -> {
                                thinkingActive = true;
                                bus.emit(new cz.krokviak.agents.api.event.AgentEvent.ThinkingDelta(thd.delta()));
                            }
                            case ModelResponseStream.Event.ToolCallDelta tcd -> {
                                collector.onToolCallDelta(tcd.toolCallId(), tcd.name(), tcd.argumentsDelta());
                                // Feed to streaming executor for eager execution
                                streamingExecutor.onToolCallDelta(tcd.toolCallId(), tcd.name(), tcd.argumentsDelta());
                            }
                            case ModelResponseStream.Event.Done done -> {
                                if (thinkingActive) {
                                    bus.emit(new cz.krokviak.agents.api.event.AgentEvent.ThinkingDone());
                                    thinkingActive = false;
                                }
                                collector.onDone(done.fullResponse());
                                // Mark all tool calls as complete for streaming executor
                                for (var tc : collector.toolCalls()) {
                                    streamingExecutor.onToolCallComplete(tc.id());
                                }
                            }
                        }
                    }
                } catch (ContextTooLongException e) {
                    if (firstEvent) bus.emit(new cz.krokviak.agents.api.event.AgentEvent.SpinnerStop());
                    streamingExecutor.shutdown();
                    if (retried) {
                        bus.emit(new cz.krokviak.agents.api.event.AgentEvent.ErrorOccurred(
                            "Context still too large after compaction. Try /compact manually."));
                        break;
                    }
                    retried = true;
                    bus.emit(new cz.krokviak.agents.api.event.AgentEvent.CompactionTriggered(
                        ctx.history().size(), -1));
                    var reactiveCompacted = ctx.compactionPipeline().reactiveCompact(ctx.history(), ctx.systemPrompt());
                    ctx.history().clear();
                    ctx.history().addAll(reactiveCompacted);
                    turn--; // retry this turn
                    continue;
                }
                retried = false;
                if (firstEvent) bus.emit(new cz.krokviak.agents.api.event.AgentEvent.SpinnerStop());
                if (collector.text() != null) {
                    bus.emit(new cz.krokviak.agents.api.event.AgentEvent.ResponseDone(0, 0));
                }

                // Track cost + token budget
                ModelResponse resp = collector.response();
                if (resp != null && resp.usage() != null) {
                    ctx.costTracker().record(ctx.modelId(), resp.usage().inputTokens(), resp.usage().outputTokens());
                    tokenBudget.recordTurn(resp.usage().inputTokens(), resp.usage().outputTokens());
                    int charCount = cz.krokviak.agents.agent.context.TokenEstimator.countChars(ctx.history());
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
                    bus.emit(new cz.krokviak.agents.api.event.AgentEvent.ErrorOccurred(
                        "Diminishing returns detected — stopping"));
                    break;
                }
            }

            if (!tokenBudget.isOverBudget() && !tokenBudget.isDiminishingReturns()) {
                ctx.eventBus().emit(new cz.krokviak.agents.api.event.AgentEvent.ErrorOccurred(
                    "Reached maximum turns (" + maxTurns + ")"));
            }
        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg == null) msg = e.getClass().getSimpleName();
            ctx.eventBus().emit(new cz.krokviak.agents.api.event.AgentEvent.ErrorOccurred(msg));
            e.printStackTrace(System.err);
        }

        saveSession(newItems);
    }

    private void injectTaskNotifications() {
        TaskManager tm = ctx.taskManager();
        if (tm == null) return;
        var bus = ctx.eventBus();
        var notifications = tm.drainNotifications();
        for (var n : notifications) {
            String msg = String.format("<task-notification>\n  task-id: %s\n  status: %s\n  description: %s\n  summary: %s\n</task-notification>",
                n.taskId(), n.status(), n.description(), n.summary());
            ctx.history().add(new InputItem.SystemMessage(msg));
            bus.emit(new cz.krokviak.agents.api.event.AgentEvent.TaskNotification(
                n.taskId(), n.description(), n.status().name(), n.summary()));
        }
    }

    private void injectMailboxMessages() {
        MailboxManager mailboxManager = ctx.mailboxManager();
        if (mailboxManager == null) return;
        var bus = ctx.eventBus();

        for (var message : mailboxManager.drain("main")) {
            String msg = String.format("<mailbox-message>\n  from: %s\n  to: %s\n  content: %s\n</mailbox-message>",
                message.sender(), message.recipient(), message.content());
            ctx.history().add(new InputItem.SystemMessage(msg));
            bus.emit(new cz.krokviak.agents.api.event.AgentEvent.MailboxMessage(
                message.sender(), message.content()));
        }
    }

    private void saveSession(List<RunItem> newItems) {
        if (ctx.session() != null && ctx.sessionId() != null && !newItems.isEmpty()) {
            try {
                ctx.session().save(ctx.sessionId(), newItems);
                updateSessionMetadata();
            } catch (Exception e) {
                log.warn("Failed to save session", e);
            }
        }
    }

    private boolean promptExtendBudget() {
        int newBudget = tokenBudget.maxBudget() * 2;
        String header = String.format("Token budget exceeded %s. Increase to %,d?", tokenBudget.format(), newBudget);

        ctx.eventBus().emit(new cz.krokviak.agents.api.event.AgentEvent.BudgetExceeded(
            tokenBudget.totalUsed(), tokenBudget.maxBudget()));

        int selected;
        try {
            selected = ctx.agent().requestQuestion(header, java.util.List.of("Yes, increase 2x", "No, stop")).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (java.util.concurrent.ExecutionException e) {
            return false;
        }

        if (selected == 0) {
            tokenBudget.extend(2);
            return true;
        }
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
            log.warn( "Failed to update session metadata", e);
        }
    }
}
