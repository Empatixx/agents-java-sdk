package cz.krokviak.agents.agent;

import cz.krokviak.agents.agent.context.ContextCompactor;
import cz.krokviak.agents.agent.context.TokenEstimator;
import cz.krokviak.agents.agent.cost.CostTracker;
import cz.krokviak.agents.agent.engine.CompactionPipeline;
import cz.krokviak.agents.agent.event.DefaultEventBus;
import cz.krokviak.agents.agent.mailbox.MailboxManager;
import cz.krokviak.agents.agent.permission.PermissionManager;
import cz.krokviak.agents.agent.plan.PlanStore;
import cz.krokviak.agents.agent.task.TaskManager;
import cz.krokviak.agents.api.AgentService;
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

/**
 * Frontend-agnostic agent state: model, session, history, managers, hooks,
 * event bus. Consumed by the engine ({@code AgentRunner}, {@code ToolDispatcher},
 * etc.) and by {@code AgentServiceImpl}. Frontend-specific contexts (e.g.
 * {@code CliContext}) extend this and add UI-only fields (renderers, dialogs).
 */
public class AgentContext {
    private final PermissionManager permissions;
    private final ContextCompactor compactor;
    private final CompactionPipeline compactionPipeline;
    private final CostTracker costTracker;
    private final TaskManager taskManager;
    private final MailboxManager mailboxManager;
    private volatile Path workingDirectory;
    private final String systemPrompt;
    private volatile ModelSettings modelSettings;

    private Model model;
    private volatile Model summaryModel;
    private String modelId;
    private String apiKey;
    private String baseUrl;
    private Session session;
    private String sessionId;
    private final List<InputItem> history;
    private volatile boolean planMode;

    private final TokenEstimator tokenEstimator = new TokenEstimator();
    private final DefaultEventBus eventBus = new DefaultEventBus();

    private PlanStore planStore;
    private AdvancedSQLiteSession advancedSession;
    private volatile AgentService agent;
    private volatile String systemPromptSuffix;
    private final cz.krokviak.agents.runner.AbortSignal abortSignal = new cz.krokviak.agents.runner.AbortSignal();
    private volatile cz.krokviak.agents.agent.hook.Hooks hooks;

    private final java.util.concurrent.ConcurrentHashMap<String, String> properties = new java.util.concurrent.ConcurrentHashMap<>();

    public AgentContext(Model model, String modelId, String apiKey, String baseUrl,
                        PermissionManager permissions, ContextCompactor compactor,
                        Path workingDirectory, String systemPrompt,
                        Session session, String sessionId,
                        TaskManager taskManager, MailboxManager mailboxManager) {
        this.model = model;
        this.modelId = modelId;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.permissions = permissions;
        this.compactor = compactor;
        this.compactionPipeline = new CompactionPipeline(compactor);
        this.compactionPipeline.setCalibratedEstimator(this.tokenEstimator);
        this.costTracker = new CostTracker();
        this.taskManager = taskManager;
        this.mailboxManager = mailboxManager;
        this.workingDirectory = workingDirectory;
        this.systemPrompt = systemPrompt;
        this.modelSettings = ModelSettings.builder().maxTokens(AgentDefaults.MODEL_MAX_TOKENS).build();
        this.session = session;
        this.sessionId = sessionId;
        this.history = Collections.synchronizedList(new ArrayList<>());
    }

    public PermissionManager permissions() { return permissions; }
    public ContextCompactor compactor() { return compactor; }
    public CompactionPipeline compactionPipeline() { return compactionPipeline; }
    public CostTracker costTracker() { return costTracker; }
    public TaskManager taskManager() { return taskManager; }
    public MailboxManager mailboxManager() { return mailboxManager; }
    public Path workingDirectory() { return workingDirectory; }
    public void setWorkingDirectory(Path p) { this.workingDirectory = p; }
    public String systemPrompt() { return systemPrompt; }
    public ModelSettings modelSettings() { return modelSettings; }
    /** Replace model settings at runtime (used by e.g. {@code /thinking} command). */
    public void setModelSettings(ModelSettings newSettings) { this.modelSettings = newSettings; }
    public Model model() { return model; }
    public String modelId() { return modelId; }
    public String apiKey() { return apiKey; }
    public String baseUrl() { return baseUrl; }
    public Session session() { return session; }
    public String sessionId() { return sessionId; }
    public List<InputItem> history() { return history; }
    public TokenEstimator tokenEstimator() { return tokenEstimator; }
    public DefaultEventBus eventBus() { return eventBus; }

    public void switchModel(String newModelId) {
        this.modelId = newModelId;
        this.model = new AnthropicModel(apiKey, baseUrl, newModelId);
    }

    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public boolean isPlanMode() { return planMode; }
    public void setPlanMode(boolean planMode) {
        this.planMode = planMode;
        onPlanModeChanged();
    }

    /** Hook for subclasses to observe plan-mode transitions. */
    protected void onPlanModeChanged() { /* default no-op */ }

    public void setPlanStore(PlanStore store) { this.planStore = store; }
    public PlanStore planStore() { return planStore; }

    public void setAdvancedSession(AdvancedSQLiteSession s) { this.advancedSession = s; }
    public AdvancedSQLiteSession advancedSession() { return advancedSession; }

    public void setAgent(AgentService agent) { this.agent = agent; }
    public AgentService agent() { return agent; }

    /**
     * Optional cheap/fast secondary model used for short summaries (tool-batch
     * labels, periodic agent progress lines). When unset, features that depend
     * on it fall back to the main {@link #model()}. The engine never hardcodes
     * a specific model id here — whoever wires this context chooses the model
     * (OpenAI, Anthropic, local, whatever).
     */
    public void setSummaryModel(Model m) { this.summaryModel = m; }
    public Model summaryModel() { return summaryModel; }
    public Model summaryModelOrMain() { return summaryModel != null ? summaryModel : model; }

    /**
     * Dynamic system-prompt addendum appended to {@link #systemPrompt()} on every
     * turn. Frontends use this to inject persistent behavior modifiers (output
     * styles, personas, per-session directives) without rebuilding the context.
     * {@code null} or blank = no suffix.
     */
    public void setSystemPromptSuffix(String suffix) { this.systemPromptSuffix = suffix; }
    public String systemPromptSuffix() { return systemPromptSuffix; }

    /**
     * Cooperative abort signal for the current run. Engine checkpoints call
     * {@link cz.krokviak.agents.runner.AbortSignal#throwIfAborted()};
     * {@link AgentService#cancelTurn()} flips it. Reset at the start of each
     * new {@code runTurn(...)}.
     */
    public cz.krokviak.agents.runner.AbortSignal abortSignal() { return abortSignal; }

    /** Shared {@link cz.krokviak.agents.agent.hook.Hooks} registry. Installed by the bootstrap. */
    public void setHooks(cz.krokviak.agents.agent.hook.Hooks hooks) { this.hooks = hooks; }
    public cz.krokviak.agents.agent.hook.Hooks hooks() { return hooks; }

    /** System prompt with active suffix appended. Engine calls this at turn time. */
    public String effectiveSystemPrompt() {
        String base = systemPrompt();
        String suf = systemPromptSuffix;
        if (suf == null || suf.isBlank()) return base;
        if (base == null || base.isBlank()) return suf;
        return base + "\n\n" + suf;
    }

    public void setProperty(String key, String value) {
        if (value == null) properties.remove(key); else properties.put(key, value);
    }
    public String getProperty(String key) { return properties.get(key); }
}
