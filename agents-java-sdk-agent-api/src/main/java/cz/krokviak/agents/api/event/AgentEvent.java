package cz.krokviak.agents.api.event;

import java.util.List;
import java.util.Map;

/**
 * UI-agnostic event hierarchy emitted by the agent lifecycle.
 *
 * Agent-side code emits events; frontends (TUI, GraphQL, ...) subscribe and
 * translate them to user-visible updates. No frontend-specific concerns here.
 */
public interface AgentEvent {

    // --- Tool lifecycle ---
    record ToolStarted(String name, Map<String, Object> args, String toolCallId, boolean inAgent) implements AgentEvent {}
    record ToolCompleted(String name, String result, int resultLines, long ms) implements AgentEvent {}
    record ToolBlocked(String name, String reason) implements AgentEvent {}

    // --- Agent lifecycle ---
    record AgentStarted(String agentId, String description) implements AgentEvent {}
    record AgentCompleted(String agentId, String result) implements AgentEvent {}
    record AgentFailed(String agentId, String error) implements AgentEvent {}
    record AgentProgress(String agentId, String detail) implements AgentEvent {}

    // --- Response streaming ---
    record ResponseDelta(String text) implements AgentEvent {}
    record ResponseDone(int inputTokens, int outputTokens) implements AgentEvent {}

    // --- Extended thinking (visible-reasoning preamble, Anthropic claude-4+ only) ---
    record ThinkingDelta(String text) implements AgentEvent {}
    record ThinkingDone() implements AgentEvent {}

    // --- Notifications ---
    record TaskNotification(String taskId, String description, String status, String summary) implements AgentEvent {}
    record MailboxMessage(String sender, String content) implements AgentEvent {}

    // --- System ---
    record ErrorOccurred(String message) implements AgentEvent {}
    record BudgetExceeded(int used, int max) implements AgentEvent {}
    record CompactionTriggered(int messagesBefore, int messagesAfter) implements AgentEvent {}
    record SessionLoaded(String sessionId, int messageCount) implements AgentEvent {}

    // --- UI-layer coordination (informational; non-TUI frontends may ignore) ---
    record SpinnerStart(String message) implements AgentEvent {}
    record SpinnerStop() implements AgentEvent {}
    record ImageAttached(String path, int index) implements AgentEvent {}

    // --- Blocking-prompt requests (agent awaits UI resolve*) ---
    record PermissionRequested(String requestId, String toolName, Map<String, Object> args, String toolCallId, List<String> options) implements AgentEvent {}
    record QuestionRequested(String requestId, String header, List<String> options) implements AgentEvent {}
    record TextInputRequested(String requestId, String header, String placeholder) implements AgentEvent {}

    // --- User actions (frontend → agent input) ---
    record UserPromptSubmitted(String text) implements AgentEvent {}
    record UserSelectionMade(String context, int selectedIndex, String selectedLabel) implements AgentEvent {}
    record UserTextInputSubmitted(String context, String value) implements AgentEvent {}
    record CommandExecuted(String name, String args) implements AgentEvent {}
    record PermissionResolved(String toolName, String toolCallId, boolean allowed) implements AgentEvent {}
}
