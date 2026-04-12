package cz.krokviak.agents.cli.service;

import cz.krokviak.agents.api.AgentService;
import cz.krokviak.agents.api.dto.*;
import cz.krokviak.agents.api.event.AgentEvent;
import cz.krokviak.agents.api.event.EventBus;
import cz.krokviak.agents.api.hook.Hook;
import cz.krokviak.agents.api.hook.HookPhase;
import cz.krokviak.agents.api.tool.ToolDescriptor;
import cz.krokviak.agents.cli.CliContext;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Concrete {@link AgentService} implementation backed by the existing CLI
 * managers held by {@link CliContext}. Lives in the CLI module during
 * Phase 1; moves to the {@code -agent} module in Phase 2.
 *
 * <p>Operations that are already wrapped by a manager delegate directly.
 * Operations that depend on legacy direct-field access (history mutation,
 * notification drain, etc.) are migrated incrementally as call sites are
 * refactored off the deprecated {@link CliContext} getters.
 */
public final class AgentServiceImpl implements AgentService {

    private final CliContext ctx;
    private final ConcurrentHashMap<String, CompletableFuture<PermissionDecision>> pendingPermissions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CompletableFuture<Integer>> pendingQuestions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CompletableFuture<String>> pendingTextInputs = new ConcurrentHashMap<>();

    private volatile cz.krokviak.agents.cli.engine.ToolDispatcher toolDispatcher;

    public AgentServiceImpl(CliContext ctx) {
        this.ctx = ctx;
        // Bridge TaskManager push-notifications onto the event bus.
        ctx.taskManager().onNotification(n ->
            ctx.eventBus().emit(new AgentEvent.TaskNotification(
                n.taskId(), n.description(),
                n.status() != null ? n.status().name() : "",
                n.summary())));
    }

    /** Plug in the ToolDispatcher so {@link #availableTools()} can list registered tools. */
    public void setToolDispatcher(cz.krokviak.agents.cli.engine.ToolDispatcher toolDispatcher) {
        this.toolDispatcher = toolDispatcher;
    }

    // -- Turn execution (delegated to AgentRunner in Phase 2; stubbed for now) --
    @Override public CompletableFuture<RunTurnResult> runTurn(RunTurnRequest req) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("runTurn wiring pending — Repl still invokes AgentRunner directly"));
    }
    @Override public void cancelTurn() { /* cooperative cancellation flag lives on AgentRunner; wired in Phase 2 */ }

    // -- Blocking-prompt async flow --
    @Override
    public CompletableFuture<PermissionDecision> requestPermission(String toolName, Map<String, Object> args, String toolCallId, List<String> options) {
        String requestId = UUID.randomUUID().toString();
        CompletableFuture<PermissionDecision> future = new CompletableFuture<>();
        pendingPermissions.put(requestId, future);
        ctx.eventBus().emit(new AgentEvent.PermissionRequested(requestId, toolName, args, toolCallId, options));
        return future;
    }

    @Override
    public void resolvePermission(String requestId, PermissionDecision decision) {
        CompletableFuture<PermissionDecision> f = pendingPermissions.remove(requestId);
        if (f != null) f.complete(decision);
    }

    @Override
    public CompletableFuture<Integer> requestQuestion(String header, List<String> options) {
        String requestId = UUID.randomUUID().toString();
        CompletableFuture<Integer> future = new CompletableFuture<>();
        pendingQuestions.put(requestId, future);
        ctx.eventBus().emit(new AgentEvent.QuestionRequested(requestId, header, options));
        return future;
    }

    @Override
    public void resolveQuestion(String requestId, int selectedIndex) {
        CompletableFuture<Integer> f = pendingQuestions.remove(requestId);
        if (f != null) f.complete(selectedIndex);
    }

    @Override
    public CompletableFuture<String> requestTextInput(String header, String placeholder) {
        String requestId = UUID.randomUUID().toString();
        CompletableFuture<String> future = new CompletableFuture<>();
        pendingTextInputs.put(requestId, future);
        ctx.eventBus().emit(new AgentEvent.TextInputRequested(requestId, header, placeholder));
        return future;
    }

    @Override
    public void resolveTextInput(String requestId, String value) {
        CompletableFuture<String> f = pendingTextInputs.remove(requestId);
        if (f != null) f.complete(value);
    }

    // -- History --
    @Override public HistorySnapshot history() {
        return new HistorySnapshot(List.copyOf(ctx.history()), /* estimatedTokens wired in Phase 2 */ 0);
    }
    @Override public void clearHistory() { ctx.history().clear(); }
    @Override public void appendHistoryItem(cz.krokviak.agents.runner.InputItem item) { ctx.history().add(item); }
    @Override public void undoLastTurn() {
        var h = ctx.history();
        synchronized (h) {
            while (!h.isEmpty()) {
                var last = h.removeLast();
                if (last instanceof cz.krokviak.agents.runner.InputItem.UserMessage) break;
            }
        }
    }
    @Override public CompletableFuture<Void> compactHistory() {
        return CompletableFuture.runAsync(() -> {
            var compacted = ctx.compactionPipeline().reactiveCompact(ctx.history(), ctx.systemPrompt());
            synchronized (ctx.history()) {
                ctx.history().clear();
                ctx.history().addAll(compacted);
            }
        });
    }

    // -- Sessions --
    @Override public List<SessionInfo> listSessions() {
        var adv = ctx.advancedSession();
        if (adv == null) return List.of();
        return adv.listSessionsWithMetadata().stream()
            .map(m -> new SessionInfo(
                m.sessionId(), /*modelId*/ null, m.title(),
                m.createdAt(), m.lastActivityAt(), m.messageCount(),
                /*preview*/ null))
            .toList();
    }
    @Override public CompletableFuture<Void> loadSession(String sessionId) {
        return CompletableFuture.runAsync(() -> {
            var loaded = ctx.session().getHistory(sessionId);
            synchronized (ctx.history()) {
                ctx.history().clear();
                ctx.history().addAll(loaded);
            }
            ctx.setSessionId(sessionId);
            ctx.eventBus().emit(new AgentEvent.SessionLoaded(sessionId, loaded.size()));
        });
    }
    @Override public String currentSessionId() { return ctx.sessionId(); }

    // -- Model --
    @Override public ModelInfo currentModel() {
        return new ModelInfo(ctx.modelId(), /*provider*/ null, ctx.baseUrl());
    }
    @Override public void switchModel(String modelId) { ctx.switchModel(modelId); }

    // -- Spawn / Tasks / Teams / Mailbox --
    @Override public CompletableFuture<String> spawnAgent(SpawnRequest req) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("spawn wiring pending — see AgentTool / AgentSpawner"));
    }
    @Override public List<TaskInfo> listTasks() {
        return ctx.taskManager().all().stream()
            .map(this::toInfo)
            .toList();
    }
    @Override public TaskInfo getTask(String taskId) {
        var t = ctx.taskManager().get(taskId);
        return t == null ? null : toInfo(t);
    }
    @Override public String createTask(String description, String initialStatus) {
        var tm = ctx.taskManager();
        String id = tm.nextId();
        cz.krokviak.agents.cli.task.TaskState.Status status = switch (
            initialStatus == null ? "pending" : initialStatus.toLowerCase()) {
            case "running", "in_progress" -> cz.krokviak.agents.cli.task.TaskState.Status.RUNNING;
            default -> cz.krokviak.agents.cli.task.TaskState.Status.PENDING;
        };
        tm.register(new cz.krokviak.agents.cli.task.TaskState(id, description, status));
        return id;
    }
    @Override public void updateTask(String taskId, String newStatus, String summary) {
        var task = ctx.taskManager().get(taskId);
        if (task == null) throw new IllegalArgumentException("No such task: " + taskId);
        switch (newStatus.toLowerCase()) {
            case "pending" -> { task.setPending(); if (summary != null) task.setResult(summary); }
            case "running", "in_progress" -> { task.start(); if (summary != null) task.setResult(summary); }
            case "completed" -> task.complete(summary != null ? summary : "Completed");
            case "failed" -> task.fail(summary != null ? summary : "Failed");
            case "killed", "stopped" -> task.kill();
            default -> throw new IllegalArgumentException("Unsupported status: " + newStatus);
        }
    }
    @Override public void stopTask(String taskId) { ctx.taskManager().killTask(taskId); }

    private TaskInfo toInfo(cz.krokviak.agents.cli.task.TaskState t) {
        return new TaskInfo(t.id(), t.description(), String.valueOf(t.status()),
            t.result() != null ? t.result() : t.error(), 0L, 0L);
    }
    @Override public List<AgentInfo> listRunningAgents() { return List.of(); /* wired in Phase 2 via AgentRegistry */ }
    @Override public List<TeamInfo> listTeams() { return List.of(); /* wired in Phase 2 via TeamManager */ }
    @Override public void sendMailbox(String from, String to, String message) {
        ctx.mailboxManager().send(from, to, message);
    }

    // -- Plan mode --
    @Override public boolean isPlanMode() { return ctx.isPlanMode(); }
    @Override public void setPlanMode(boolean on) { ctx.setPlanMode(on); }

    // -- Introspection --
    @Override public CostSummary costs() {
        var t = ctx.costTracker();
        return new CostSummary(t.totalInputTokens(), t.totalOutputTokens(), t.totalCostUsd(), t.format());
    }
    @Override public List<PermissionRule> permissionRules() {
        return ctx.permissions().sessionRules().stream()
            .map(r -> new PermissionRule(
                r.toolName() + (r.pattern() != null ? "[" + r.pattern() + "]" : ""),
                r.behavior().name()))
            .toList();
    }
    @Override public List<ToolDescriptor> availableTools() {
        if (toolDispatcher == null) return List.of();
        return toolDispatcher.all().stream()
            .map(t -> new ToolDescriptor(t.name(), t.description(),
                t.definition() != null ? t.definition().parametersSchema() : Map.of()))
            .toList();
    }

    // -- Extension --
    @Override @SuppressWarnings({"unchecked", "rawtypes"})
    public <E> void registerHook(HookPhase phase, Hook<E> hook) {
        /* hook registry lives outside the service in Phase 1; delegated in Phase 2 */
        throw new UnsupportedOperationException("Use Hooks registry directly until Phase 2");
    }

    @Override public EventBus events() { return ctx.eventBus(); }
}
