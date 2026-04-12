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
 * UI-agnostic facade exposing all agent lifecycle operations. The primary
 * contract between an agent engine and any frontend (TUI, GraphQL, HTTP, IDE
 * plugin, …).
 *
 * <p>Frontends consume the agent exclusively through this interface and the
 * {@link #events() event bus} — there is no stable API to reach internal
 * managers (TaskManager, PermissionManager, etc.). This keeps the engine free
 * to refactor behind the facade.
 *
 * <h2>Synchronous vs. asynchronous</h2>
 * Methods returning {@link CompletableFuture} perform I/O, model calls, or
 * user-blocking prompts — callers should await them on a thread that can
 * tolerate the latency (never the render thread). Methods returning plain
 * values are in-memory snapshots or lightweight setters and return immediately.
 *
 * <h2>Blocking user prompts</h2>
 * Permission / question / text-input prompts are modeled as event + resolve:
 * the engine emits {@code PermissionRequested} (and companions) on the bus
 * and returns a future; the frontend shows its dialog and calls the matching
 * {@code resolve*} method with the user's decision. Frontends never block
 * the engine — the future completes when resolve is called, or eventually
 * times out on the engine side.
 */
public interface AgentService {

    // -- Turn execution --

    /**
     * Run one turn of the agent: send {@code req.userText()} (plus any
     * attached context) to the model, execute any resulting tool calls,
     * iterate until the model stops. Streams events on {@link #events()}
     * during the run; returns {@link RunTurnResult} on completion.
     *
     * @param req turn input (user text, attachments, optional max-turns override)
     * @return future completing once the turn ends (normally or via cancel)
     */
    CompletableFuture<RunTurnResult> runTurn(RunTurnRequest req);

    /**
     * Request the in-flight turn stop at the next safe checkpoint
     * (typically sub-second). Idempotent; safe to call from any thread.
     * The future returned by the originating {@link #runTurn} completes
     * with {@code interrupted = true}.
     */
    void cancelTurn();

    // -- Blocking-prompt resolutions (responses to *Requested events) --

    /** Complete a pending {@code PermissionRequested} with the user's decision. */
    void resolvePermission(String requestId, PermissionDecision decision);

    /** Complete a pending {@code QuestionRequested} with the chosen option index (0-based; -1 = no answer). */
    void resolveQuestion(String requestId, int selectedIndex);

    /** Complete a pending {@code TextInputRequested} with the user-entered text. */
    void resolveTextInput(String requestId, String value);

    /**
     * Request permission from the frontend before executing a sensitive tool
     * (e.g. {@code bash}). Emits {@link AgentEvent.PermissionRequested} on
     * the bus; the returned future completes when
     * {@link #resolvePermission} is called.
     *
     * <p><b>Thread-safety:</b> callers must NOT run on the same thread that
     * services the event bus / frontend dialog — that's deadlock.
     *
     * @param toolName    tool requesting permission (for display)
     * @param args        tool arguments (for display; frontend may show target path, command, …)
     * @param toolCallId  opaque id for correlating with the tool invocation (may be {@code null})
     * @param options     UI-label strings for the dialog buttons
     * @return future of the user's decision
     */
    CompletableFuture<PermissionDecision> requestPermission(String toolName,
                                                            java.util.Map<String, Object> args,
                                                            String toolCallId,
                                                            List<String> options);

    /**
     * Ask the user a multiple-choice question. Emits {@link AgentEvent.QuestionRequested};
     * the returned future completes with the chosen option index.
     *
     * @param header  question header / prompt
     * @param options labels of selectable choices
     */
    CompletableFuture<Integer> requestQuestion(String header, List<String> options);

    /**
     * Ask the user for free-form text input. Emits {@link AgentEvent.TextInputRequested};
     * the returned future completes with the entered text.
     *
     * @param header      prompt text
     * @param placeholder hint shown in the empty input field; may be {@code null}
     */
    CompletableFuture<String> requestTextInput(String header, String placeholder);

    // -- History --

    /** @return immutable snapshot of the conversation history right now */
    HistorySnapshot history();

    /** Drop all messages from the session history. In-memory and persistent state both cleared. */
    void clearHistory();

    /** Remove the last user turn + any assistant reply + tool calls it produced. */
    void undoLastTurn();

    /**
     * Run the compaction pipeline on the current history. Useful for freeing
     * budget explicitly; otherwise the engine runs compaction automatically
     * when thresholds are hit.
     *
     * @return future completing once compaction settles (may involve a model call)
     */
    CompletableFuture<Void> compactHistory();

    /**
     * Append a pre-turn context item to history (attached image, pasted
     * reference, system breadcrumb). Called by frontends before
     * {@link #runTurn} so the next turn sees the context.
     */
    void appendHistoryItem(cz.krokviak.agents.runner.InputItem item);

    // -- Sessions --

    /** @return metadata of all persisted sessions (most recent first) */
    List<SessionInfo> listSessions();

    /** Load the session with the given id into memory, replacing the current history. */
    CompletableFuture<Void> loadSession(String sessionId);

    /** @return id of the currently loaded session; {@code null} before first {@link #runTurn} */
    String currentSessionId();

    // -- Model --

    /** @return identity of the currently active model */
    ModelInfo currentModel();

    /**
     * Switch the active model for subsequent turns. Takes effect on the next
     * {@link #runTurn}; the in-flight turn (if any) keeps its existing model.
     */
    void switchModel(String modelId);

    // -- Sub-agents / tasks / teams / mailbox --

    /**
     * Spawn a sub-agent.
     * @param req spawn parameters (name, prompt, background flag, model override, …)
     * @return future of the sub-agent's id (background) or final output string (foreground)
     */
    CompletableFuture<String> spawnAgent(SpawnRequest req);

    /** @return snapshot of all tasks tracked by the agent (pending, running, terminal) */
    List<TaskInfo> listTasks();

    /** @return single task by id, or {@code null} if no match */
    TaskInfo getTask(String taskId);

    /**
     * Register a new task in the task manager.
     * @param description task description
     * @param initialStatus {@code "pending"} or {@code "running"}
     * @return the generated task id
     */
    String createTask(String description, String initialStatus);

    /**
     * Transition a task's state.
     * @param taskId    task to update
     * @param newStatus target status ({@code pending|running|completed|failed|killed})
     * @param summary   result/error text; may be {@code null}
     */
    void updateTask(String taskId, String newStatus, String summary);

    /** Force-stop a task. Sends interrupt to its worker thread if running. */
    void stopTask(String taskId);

    /** @return running sub-agents (STARTING / RUNNING status only) */
    List<AgentInfo> listRunningAgents();

    /** @return named teams */
    List<TeamInfo> listTeams();

    /**
     * Send an inter-agent mailbox message.
     * @param from    sender label
     * @param to      recipient mailbox (agent id or {@code "main"})
     * @param message body
     */
    void sendMailbox(String from, String to, String message);

    // -- Plan mode --

    /** @return {@code true} if plan mode is currently active */
    boolean isPlanMode();

    /** Toggle plan mode on/off. Engines use this to gate write tools. */
    void setPlanMode(boolean on);

    // -- Introspection --

    /** @return aggregate token + USD cost counters for this session */
    CostSummary costs();

    /** @return session-scoped permission rules accumulated so far */
    List<PermissionRule> permissionRules();

    /** @return descriptions of all registered tools (for listing UIs / dashboards) */
    List<ToolDescriptor> availableTools();

    // -- Extension --

    /**
     * Register a {@link Hook} for a given {@link HookPhase}. Hooks run
     * inline during dispatch; keep them fast and non-blocking.
     *
     * @param phase lifecycle phase to hook into (see {@link HookPhase})
     * @param hook  handler receiving the phase's typed event
     */
    <E> void registerHook(HookPhase phase, Hook<E> hook);

    // -- Event bus access (primary output channel) --

    /**
     * @return the shared event bus. Frontends {@code subscribe} / {@code on}
     *         to render output; agents {@code emit} to announce lifecycle
     *         transitions (tool started, response delta, permission requested, …).
     */
    EventBus events();

    /** Convenience: emit an event on the bus. */
    default void emit(AgentEvent event) { events().emit(event); }
}
