package cz.krokviak.agents.cli.event;

import cz.krokviak.agents.api.event.AgentEvent;

import cz.krokviak.agents.cli.render.Renderer;

/**
 * Wires CliEvents to Renderer output.
 * This replaces all direct ctx.output().printXxx() calls in the engine.
 */
public final class RenderEventListener {

    private final Renderer renderer;

    public RenderEventListener(Renderer renderer) {
        this.renderer = renderer;
    }

    public void register(cz.krokviak.agents.api.event.EventBus bus) {
        bus.subscribe(this::handle);
    }

    private void handle(AgentEvent event) {
        switch (event) {
            case AgentEvent.ToolStarted e ->
                renderer.printToolCall(e.name(), e.args());
            case AgentEvent.ToolCompleted e ->
                renderer.printToolResult(e.name(), e.result());
            case AgentEvent.ToolBlocked e ->
                renderer.printPermissionDenied(e.name());
            case AgentEvent.AgentStarted e -> {
                renderer.setCurrentAgent(e.agentId());
                renderer.renderAgentStatus(e.agentId(),
                    cz.krokviak.agents.agent.spawn.AgentStatus.RUNNING, e.description());
            }
            case AgentEvent.AgentCompleted e -> {
                renderer.renderAgentStatus(e.agentId(),
                    cz.krokviak.agents.agent.spawn.AgentStatus.COMPLETED, "done");
                renderer.clearCurrentAgent();
            }
            case AgentEvent.AgentFailed e -> {
                renderer.renderAgentStatus(e.agentId(),
                    cz.krokviak.agents.agent.spawn.AgentStatus.FAILED, e.error());
                renderer.clearCurrentAgent();
            }
            case AgentEvent.AgentProgress e ->
                renderer.renderAgentStatus(e.agentId(),
                    cz.krokviak.agents.agent.spawn.AgentStatus.RUNNING, e.detail());
            case AgentEvent.ResponseDelta e ->
                renderer.printTextDelta(e.text());
            case AgentEvent.ResponseDone _ -> renderer.println("");
            case AgentEvent.ThinkingDelta e ->
                renderer.printTextDelta("\033[2;3m" + e.text() + "\033[0m");
            case AgentEvent.ThinkingDone _ -> renderer.println("");
            case AgentEvent.ToolBatchSummary e ->
                renderer.println("\033[2m↳ " + e.label() + "\033[0m");
            case AgentEvent.SpinnerStart e ->
                renderer.startSpinner(e.message());
            case AgentEvent.SpinnerStop _ ->
                renderer.stopSpinner();
            case AgentEvent.ErrorOccurred e ->
                renderer.printError(e.message());
            case AgentEvent.TaskNotification e -> {
                String icon = switch (e.status()) {
                    case "COMPLETED" -> "\u2713";
                    case "FAILED" -> "\u2717";
                    default -> "\u25cf";
                };
                renderer.println("");
                renderer.println("  " + icon + " " + e.description() + " " + e.status().toLowerCase());
                if (e.summary() != null && !e.summary().isBlank()) {
                    String summary = e.summary().length() > 150
                        ? e.summary().substring(0, 150) + "..." : e.summary();
                    renderer.println("    " + summary);
                }
            }
            case AgentEvent.MailboxMessage e ->
                renderer.println("  [" + e.sender() + "] " + e.content());
            case AgentEvent.BudgetExceeded _ -> {} // handled by AgentRunner prompt
            case AgentEvent.CompactionTriggered e ->
                renderer.println("  [Compacted: " + e.messagesBefore() + " → " + e.messagesAfter() + " messages]");
            case AgentEvent.SessionLoaded e ->
                renderer.println("  Loaded " + e.messageCount() + " messages from session: " + e.sessionId());
            case AgentEvent.ImageAttached e ->
                renderer.println("  \ud83d\uddbc [Image #" + e.index() + "] " + e.path());
            // User events — consumed by business logic listeners, not renderer
            case AgentEvent.UserPromptSubmitted _ -> {}
            case AgentEvent.UserSelectionMade _ -> {}
            case AgentEvent.UserTextInputSubmitted _ -> {}
            case AgentEvent.CommandExecuted _ -> {}
            case AgentEvent.PermissionResolved _ -> {}
            case AgentEvent.PermissionRequested _ -> {}
            case AgentEvent.QuestionRequested _ -> {}
            case AgentEvent.TextInputRequested _ -> {}
            default -> {}
        }
    }
}
