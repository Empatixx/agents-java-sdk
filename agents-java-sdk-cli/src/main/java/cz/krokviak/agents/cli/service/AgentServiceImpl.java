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

    public AgentServiceImpl(CliContext ctx) {
        this.ctx = ctx;
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
        /* wired in Phase 2 — shape of AdvancedSQLiteSession.listSessionsWithMetadata() to be mapped */
        return List.of();
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
            .map(t -> new TaskInfo(t.id(), t.description(), String.valueOf(t.status()), null, 0L, 0L))
            .toList();
    }
    @Override public void stopTask(String taskId) { ctx.taskManager().killTask(taskId); }
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
        /* mapped against CostTracker API in Phase 2 */
        return new CostSummary(0L, 0L, 0.0, "");
    }
    @Override public List<PermissionRule> permissionRules() {
        /* mapped against PermissionManager API in Phase 2 */
        return List.of();
    }
    @Override public List<ToolDescriptor> availableTools() {
        return List.of(); /* wired in Phase 2 via ToolRegistry */
    }

    // -- Extension --
    @Override @SuppressWarnings({"unchecked", "rawtypes"})
    public <E> void registerHook(HookPhase phase, Hook<E> hook) {
        /* hook registry lives outside the service in Phase 1; delegated in Phase 2 */
        throw new UnsupportedOperationException("Use Hooks registry directly until Phase 2");
    }

    @Override public EventBus events() { return ctx.eventBus(); }
}
