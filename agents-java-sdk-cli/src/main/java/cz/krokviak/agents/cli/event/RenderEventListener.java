package cz.krokviak.agents.cli.event;

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

    public void register(CliEventBus bus) {
        bus.subscribe(this::handle);
    }

    private void handle(CliEvent event) {
        switch (event) {
            case CliEvent.ToolStarted e ->
                renderer.printToolCall(e.name(), e.args());
            case CliEvent.ToolCompleted e ->
                renderer.printToolResult(e.name(), e.result());
            case CliEvent.ToolBlocked e ->
                renderer.printPermissionDenied(e.name());
            case CliEvent.AgentStarted e -> {
                renderer.setCurrentAgent(e.agentId());
                renderer.renderAgentStatus(e.agentId(),
                    cz.krokviak.agents.cli.render.AgentStatus.RUNNING, e.description());
            }
            case CliEvent.AgentCompleted e -> {
                renderer.renderAgentStatus(e.agentId(),
                    cz.krokviak.agents.cli.render.AgentStatus.COMPLETED, "done");
                renderer.clearCurrentAgent();
            }
            case CliEvent.AgentFailed e -> {
                renderer.renderAgentStatus(e.agentId(),
                    cz.krokviak.agents.cli.render.AgentStatus.FAILED, e.error());
                renderer.clearCurrentAgent();
            }
            case CliEvent.AgentProgress e ->
                renderer.renderAgentStatus(e.agentId(),
                    cz.krokviak.agents.cli.render.AgentStatus.RUNNING, e.detail());
            case CliEvent.ResponseDelta e ->
                renderer.printTextDelta(e.text());
            case CliEvent.ResponseDone _ -> renderer.println("");
            case CliEvent.SpinnerStart e ->
                renderer.startSpinner(e.message());
            case CliEvent.SpinnerStop _ ->
                renderer.stopSpinner();
            case CliEvent.ErrorOccurred e ->
                renderer.printError(e.message());
            case CliEvent.TaskNotification e -> {
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
            case CliEvent.MailboxMessage e ->
                renderer.println("  [" + e.sender() + "] " + e.content());
            case CliEvent.BudgetExceeded _ -> {} // handled by AgentRunner prompt
            case CliEvent.CompactionTriggered e ->
                renderer.println("  [Compacted: " + e.messagesBefore() + " → " + e.messagesAfter() + " messages]");
            case CliEvent.SessionLoaded e ->
                renderer.println("  Loaded " + e.messageCount() + " messages from session: " + e.sessionId());
            case CliEvent.ImageAttached e ->
                renderer.println("  \ud83d\uddbc [Image #" + e.index() + "] " + e.path());
            // User events — consumed by business logic listeners, not renderer
            case CliEvent.UserPromptSubmitted _ -> {}
            case CliEvent.UserSelectionMade _ -> {}
            case CliEvent.UserTextInputSubmitted _ -> {}
            case CliEvent.CommandExecuted _ -> {}
            case CliEvent.PermissionDecision _ -> {}
        }
    }
}
