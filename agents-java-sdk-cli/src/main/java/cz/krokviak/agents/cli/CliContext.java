package cz.krokviak.agents.cli;

import cz.krokviak.agents.cli.context.ContextCompactor;
import cz.krokviak.agents.cli.cost.CostTracker;
import cz.krokviak.agents.cli.engine.CompactionPipeline;
import cz.krokviak.agents.cli.mailbox.MailboxManager;
import cz.krokviak.agents.cli.permission.PermissionManager;
import cz.krokviak.agents.cli.task.TaskManager;
import cz.krokviak.agents.model.AnthropicModel;
import cz.krokviak.agents.model.Model;
import cz.krokviak.agents.model.ModelSettings;
import cz.krokviak.agents.runner.InputItem;
import cz.krokviak.agents.session.Session;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CliContext {
    private final TerminalOutput output;
    private final PermissionManager permissions;
    private final ContextCompactor compactor;
    private final CompactionPipeline compactionPipeline;
    private final CostTracker costTracker;
    private final TaskManager taskManager;
    private final MailboxManager mailboxManager;
    private final Path workingDirectory;
    private final String systemPrompt;
    private final ModelSettings modelSettings;

    private Model model;
    private String modelId;
    private String apiKey;
    private String baseUrl;
    private Session session;
    private String sessionId;
    private final List<InputItem> history;
    private volatile boolean planMode;

    public CliContext(Model model, String modelId, String apiKey, String baseUrl,
                     TerminalOutput output, PermissionManager permissions,
                     ContextCompactor compactor, Path workingDirectory,
                     String systemPrompt, Session session, String sessionId,
                     TaskManager taskManager, MailboxManager mailboxManager) {
        this.model = model;
        this.modelId = modelId;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.output = output;
        this.permissions = permissions;
        this.compactor = compactor;
        this.compactionPipeline = new CompactionPipeline(compactor);
        this.costTracker = new CostTracker();
        this.taskManager = taskManager;
        this.mailboxManager = mailboxManager;
        this.workingDirectory = workingDirectory;
        this.systemPrompt = systemPrompt;
        this.modelSettings = ModelSettings.builder().maxTokens(16384).build();
        this.session = session;
        this.sessionId = sessionId;
        this.history = Collections.synchronizedList(new ArrayList<>());
    }

    public TerminalOutput output() { return output; }
    public PermissionManager permissions() { return permissions; }
    public ContextCompactor compactor() { return compactor; }
    public CompactionPipeline compactionPipeline() { return compactionPipeline; }
    public CostTracker costTracker() { return costTracker; }
    public TaskManager taskManager() { return taskManager; }
    public MailboxManager mailboxManager() { return mailboxManager; }
    public Path workingDirectory() { return workingDirectory; }
    public String systemPrompt() { return systemPrompt; }
    public ModelSettings modelSettings() { return modelSettings; }
    public Model model() { return model; }
    public String modelId() { return modelId; }
    public String apiKey() { return apiKey; }
    public String baseUrl() { return baseUrl; }
    public Session session() { return session; }
    public String sessionId() { return sessionId; }
    public List<InputItem> history() { return history; }

    public void switchModel(String newModelId) {
        this.modelId = newModelId;
        this.model = new AnthropicModel(apiKey, baseUrl, newModelId);
    }

    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public boolean isPlanMode() { return planMode; }
    public void setPlanMode(boolean planMode) { this.planMode = planMode; }
}
