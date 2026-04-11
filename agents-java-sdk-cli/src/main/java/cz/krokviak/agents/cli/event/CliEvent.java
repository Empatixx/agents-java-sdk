package cz.krokviak.agents.cli.event;

import java.util.Map;

/**
 * Sealed event hierarchy for CLI lifecycle.
 * Components emit events, listeners react — no direct coupling.
 */
public sealed interface CliEvent {

    // --- Tool lifecycle ---
    record ToolStarted(String name, Map<String, Object> args, String toolCallId, boolean inAgent) implements CliEvent {}
    record ToolCompleted(String name, String result, int resultLines, long ms) implements CliEvent {}
    record ToolBlocked(String name, String reason) implements CliEvent {}

    // --- Agent lifecycle ---
    record AgentStarted(String agentId, String description) implements CliEvent {}
    record AgentCompleted(String agentId, String result) implements CliEvent {}
    record AgentFailed(String agentId, String error) implements CliEvent {}
    record AgentProgress(String agentId, String detail) implements CliEvent {}

    // --- Response streaming ---
    record ResponseDelta(String text) implements CliEvent {}
    record ResponseDone(int inputTokens, int outputTokens) implements CliEvent {}

    // --- Notifications ---
    record TaskNotification(String taskId, String description, String status, String summary) implements CliEvent {}
    record MailboxMessage(String sender, String content) implements CliEvent {}

    // --- System ---
    record SpinnerStart(String message) implements CliEvent {}
    record SpinnerStop() implements CliEvent {}
    record ErrorOccurred(String message) implements CliEvent {}
    record BudgetExceeded(int used, int max) implements CliEvent {}
    record CompactionTriggered(int messagesBefore, int messagesAfter) implements CliEvent {}
    record SessionLoaded(String sessionId, int messageCount) implements CliEvent {}

    // --- User actions (TUI → business logic) ---
    /** User submitted a prompt in the input field. */
    record UserPromptSubmitted(String text) implements CliEvent {}
    /** User made a selection in a prompt dialog. context identifies which dialog. */
    record UserSelectionMade(String context, int selectedIndex, String selectedLabel) implements CliEvent {}
    /** User submitted text in a text input dialog. */
    record UserTextInputSubmitted(String context, String value) implements CliEvent {}
    /** User executed a slash command. */
    record CommandExecuted(String name, String args) implements CliEvent {}
    /** User granted or denied permission for a tool call. */
    record PermissionDecision(String toolName, String toolCallId, boolean allowed) implements CliEvent {}
}
