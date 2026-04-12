package cz.krokviak.agents.cli;

import cz.krokviak.agents.agent.AgentContext;
import cz.krokviak.agents.agent.context.ContextCompactor;
import cz.krokviak.agents.agent.mailbox.MailboxManager;
import cz.krokviak.agents.agent.permission.PermissionManager;
import cz.krokviak.agents.agent.task.TaskManager;
import cz.krokviak.agents.cli.render.Renderer;
import cz.krokviak.agents.model.Model;
import cz.krokviak.agents.session.Session;

import java.nio.file.Path;

/**
 * CLI-specific context. Extends {@link AgentContext} with UI-only fields
 * (renderers, interactive prompt handlers, MCP manager, plan-mode listener).
 */
public final class CliContext extends AgentContext {
    private final Renderer output;
    private final java.util.concurrent.atomic.AtomicReference<Runnable> planModeListener =
        new java.util.concurrent.atomic.AtomicReference<>();
    private cz.krokviak.agents.cli.render.PromptRenderer promptRenderer;
    private cz.krokviak.agents.cli.mcp.McpManager mcpManager;

    public CliContext(Model model, String modelId, String apiKey, String baseUrl,
                     Renderer output, PermissionManager permissions,
                     ContextCompactor compactor, Path workingDirectory,
                     String systemPrompt, Session session, String sessionId,
                     TaskManager taskManager, MailboxManager mailboxManager) {
        super(model, modelId, apiKey, baseUrl, permissions, compactor,
            workingDirectory, systemPrompt, session, sessionId, taskManager, mailboxManager);
        this.output = output;
    }

    public Renderer output() { return output; }

    @Override
    protected void onPlanModeChanged() {
        Runnable listener = planModeListener.get();
        if (listener != null) listener.run();
    }
    public void onPlanModeChange(Runnable listener) { this.planModeListener.set(listener); }

    public void setPromptRenderer(cz.krokviak.agents.cli.render.PromptRenderer r) { this.promptRenderer = r; }
    public cz.krokviak.agents.cli.render.PromptRenderer promptRenderer() { return promptRenderer; }

    /** @deprecated Use promptRenderer() instead */
    @Deprecated
    public cz.krokviak.agents.cli.render.tui.TuiRenderer tuiRenderer() {
        return promptRenderer instanceof cz.krokviak.agents.cli.render.tui.TuiRenderer t ? t : null;
    }

    public void setMcpManager(cz.krokviak.agents.cli.mcp.McpManager m) { this.mcpManager = m; }
    public cz.krokviak.agents.cli.mcp.McpManager mcpManager() { return mcpManager; }
}
