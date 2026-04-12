package cz.krokviak.agents.api;

import cz.krokviak.agents.api.dto.*;
import cz.krokviak.agents.api.event.AgentEvent;
import cz.krokviak.agents.api.event.EventBus;
import cz.krokviak.agents.api.hook.Hook;
import cz.krokviak.agents.api.hook.HookPhase;
import cz.krokviak.agents.api.tool.ToolDescriptor;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * UI-agnostic facade exposing all agent lifecycle operations.
 *
 * Frontends (TUI, GraphQL, ...) consume exclusively through this interface
 * and {@link #events()}; there is no direct access to internal managers.
 *
 * Blocking user prompts (permission / question / text input) are modeled as
 * events emitted by the agent + resolve* calls from the frontend — the
 * agent side awaits a completable future completed by the frontend.
 */
public interface AgentService {

    // -- Turn execution --
    CompletableFuture<RunTurnResult> runTurn(RunTurnRequest req);
    void cancelTurn();

    // -- Blocking-prompt resolutions (responses to *Requested events) --
    void resolvePermission(String requestId, PermissionDecision decision);
    void resolveQuestion(String requestId, int selectedIndex);
    void resolveTextInput(String requestId, String value);

    /**
     * Request permission from the frontend. Emits a PermissionRequested
     * event and returns a future completed by {@link #resolvePermission}.
     * Thread-safety: callers must NOT run on the same thread that services
     * the event bus / frontend dialog, or deadlock results.
     */
    CompletableFuture<PermissionDecision> requestPermission(String toolName,
                                                            java.util.Map<String, Object> args,
                                                            String toolCallId,
                                                            List<String> options);

    CompletableFuture<Integer> requestQuestion(String header, List<String> options);

    CompletableFuture<String> requestTextInput(String header, String placeholder);

    // -- History --
    HistorySnapshot history();
    void clearHistory();
    void undoLastTurn();
    CompletableFuture<Void> compactHistory();
    /**
     * Append a pre-turn context item to history (e.g. an attached image,
     * pasted reference, or system breadcrumb). Called by frontends before
     * {@link #runTurn} so the next turn sees the context.
     */
    void appendHistoryItem(cz.krokviak.agents.runner.InputItem item);

    // -- Sessions --
    List<SessionInfo> listSessions();
    CompletableFuture<Void> loadSession(String sessionId);
    String currentSessionId();

    // -- Model --
    ModelInfo currentModel();
    void switchModel(String modelId);

    // -- Sub-agents / tasks / teams / mailbox --
    CompletableFuture<String> spawnAgent(SpawnRequest req);
    List<TaskInfo> listTasks();
    void stopTask(String taskId);
    List<AgentInfo> listRunningAgents();
    List<TeamInfo> listTeams();
    void sendMailbox(String from, String to, String message);

    // -- Plan mode --
    boolean isPlanMode();
    void setPlanMode(boolean on);

    // -- Introspection --
    CostSummary costs();
    List<PermissionRule> permissionRules();
    List<ToolDescriptor> availableTools();

    // -- Extension --
    <E> void registerHook(HookPhase phase, Hook<E> hook);

    // -- Event bus access (primary output channel) --
    EventBus events();

    /** Convenience: emit an event on the bus. */
    default void emit(AgentEvent event) { events().emit(event); }
}
