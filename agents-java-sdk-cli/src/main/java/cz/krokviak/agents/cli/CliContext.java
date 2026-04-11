package cz.krokviak.agents.cli;

import cz.krokviak.agents.cli.context.ContextCompactor;
import cz.krokviak.agents.cli.context.TokenEstimator;
import cz.krokviak.agents.cli.cost.CostTracker;
import cz.krokviak.agents.cli.engine.CompactionPipeline;
import cz.krokviak.agents.cli.mailbox.MailboxManager;
import cz.krokviak.agents.cli.permission.PermissionManager;
import cz.krokviak.agents.cli.render.Renderer;
import cz.krokviak.agents.cli.task.TaskManager;
import cz.krokviak.agents.model.AnthropicModel;
import cz.krokviak.agents.model.Model;
import cz.krokviak.agents.model.ModelSettings;
import cz.krokviak.agents.runner.InputItem;
import cz.krokviak.agents.session.AdvancedSQLiteSession;
import cz.krokviak.agents.session.Session;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CliContext {
    private final Renderer output;
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
    private final java.util.concurrent.atomic.AtomicReference<Runnable> planModeListener = new java.util.concurrent.atomic.AtomicReference<>();

    public CliContext(Model model, String modelId, String apiKey, String baseUrl,
                     Renderer output, PermissionManager permissions,
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
        this.compactionPipeline.setCalibratedEstimator(this.tokenEstimator);
        this.costTracker = new CostTracker();
        this.taskManager = taskManager;
        this.mailboxManager = mailboxManager;
        this.workingDirectory = workingDirectory;
        this.systemPrompt = systemPrompt;
        this.modelSettings = ModelSettings.builder().maxTokens(CliDefaults.MODEL_MAX_TOKENS).build();
        this.session = session;
        this.sessionId = sessionId;
        this.history = Collections.synchronizedList(new ArrayList<>());
    }

    public Renderer output() { return output; }
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
    public void setPlanMode(boolean planMode) {
        this.planMode = planMode;
        Runnable listener = planModeListener.get();
        if (listener != null) listener.run();
    }
    public void onPlanModeChange(Runnable listener) { this.planModeListener.set(listener); }

    private cz.krokviak.agents.cli.plan.PlanStore planStore;
    public void setPlanStore(cz.krokviak.agents.cli.plan.PlanStore store) { this.planStore = store; }
    public cz.krokviak.agents.cli.plan.PlanStore planStore() { return planStore; }

    private cz.krokviak.agents.cli.render.PromptRenderer promptRenderer;
    public void setPromptRenderer(cz.krokviak.agents.cli.render.PromptRenderer r) { this.promptRenderer = r; }
    public cz.krokviak.agents.cli.render.PromptRenderer promptRenderer() { return promptRenderer; }

    /** @deprecated Use promptRenderer() instead */
    public cz.krokviak.agents.cli.render.tui.TuiRenderer tuiRenderer() {
        return promptRenderer instanceof cz.krokviak.agents.cli.render.tui.TuiRenderer t ? t : null;
    }

    private AdvancedSQLiteSession advancedSession;
    public void setAdvancedSession(AdvancedSQLiteSession s) { this.advancedSession = s; }
    public AdvancedSQLiteSession advancedSession() { return advancedSession; }

    private final TokenEstimator tokenEstimator = new TokenEstimator();
    public TokenEstimator tokenEstimator() { return tokenEstimator; }

    private final cz.krokviak.agents.cli.event.CliEventBus eventBus = new cz.krokviak.agents.cli.event.CliEventBus();
    public cz.krokviak.agents.cli.event.CliEventBus eventBus() { return eventBus; }

    private final java.util.concurrent.ConcurrentHashMap<String, String> properties = new java.util.concurrent.ConcurrentHashMap<>();
    public void setProperty(String key, String value) {
        if (value == null) properties.remove(key); else properties.put(key, value);
    }
    public String getProperty(String key) { return properties.get(key); }
}
