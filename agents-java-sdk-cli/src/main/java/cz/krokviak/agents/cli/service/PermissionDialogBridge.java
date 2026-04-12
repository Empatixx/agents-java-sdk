package cz.krokviak.agents.cli.service;

import cz.krokviak.agents.api.AgentService;
import cz.krokviak.agents.api.dto.PermissionDecision;
import cz.krokviak.agents.api.event.AgentEvent;
import cz.krokviak.agents.cli.render.PromptRenderer;

/**
 * Bridges {@link AgentEvent.PermissionRequested}/{@link AgentEvent.QuestionRequested}/
 * {@link AgentEvent.TextInputRequested} events to the TUI {@link PromptRenderer}
 * dialogs, running the blocking dialog on a virtual thread so the agent-side
 * caller's {@link java.util.concurrent.CompletableFuture#get()} can complete
 * without deadlock.
 *
 * A GraphQL or web frontend installs its own bridge that, instead of showing
 * a terminal dialog, transforms the event into a subscription push and
 * awaits a {@code resolvePermission} mutation.
 */
public final class PermissionDialogBridge {

    private final AgentService agent;
    private final PromptRenderer prompts;

    public PermissionDialogBridge(AgentService agent, PromptRenderer prompts) {
        this.agent = agent;
        this.prompts = prompts;
    }

    public void install() {
        agent.events().on(AgentEvent.PermissionRequested.class, this::onPermission);
        agent.events().on(AgentEvent.QuestionRequested.class, this::onQuestion);
        agent.events().on(AgentEvent.TextInputRequested.class, this::onTextInput);
    }

    private void onPermission(AgentEvent.PermissionRequested e) {
        if (prompts == null) { agent.resolvePermission(e.requestId(), PermissionDecision.DENY); return; }
        Thread.ofVirtual().name("perm-dialog-" + e.requestId()).start(() -> {
            try {
                String header = "⚠ Allow " + e.toolName() + "?";
                int idx = prompts.promptSelection(header, e.options().toArray(new String[0]));
                agent.resolvePermission(e.requestId(), switch (idx) {
                    case 0 -> PermissionDecision.ALLOW_ONCE;
                    case 1 -> PermissionDecision.ALLOW_FOR_SESSION;
                    default -> PermissionDecision.DENY;
                });
            } catch (Exception ex) {
                agent.resolvePermission(e.requestId(), PermissionDecision.DENY);
            }
        });
    }

    private void onQuestion(AgentEvent.QuestionRequested e) {
        if (prompts == null) { agent.resolveQuestion(e.requestId(), -1); return; }
        Thread.ofVirtual().name("question-dialog-" + e.requestId()).start(() -> {
            try {
                int idx = prompts.promptSelection(e.header(), e.options().toArray(new String[0]));
                agent.resolveQuestion(e.requestId(), idx);
            } catch (Exception ex) {
                agent.resolveQuestion(e.requestId(), -1);
            }
        });
    }

    private void onTextInput(AgentEvent.TextInputRequested e) {
        if (prompts == null) { agent.resolveTextInput(e.requestId(), ""); return; }
        Thread.ofVirtual().name("text-dialog-" + e.requestId()).start(() -> {
            try {
                String value = prompts.promptTextInput(e.header(), e.placeholder());
                agent.resolveTextInput(e.requestId(), value == null ? "" : value);
            } catch (Exception ex) {
                agent.resolveTextInput(e.requestId(), "");
            }
        });
    }
}
