package cz.krokviak.agents.runner;

import cz.krokviak.agents.hook.RunHooks;
import cz.krokviak.agents.model.Model;
import cz.krokviak.agents.session.Session;

public final class RunConfig<T> {
    private final T context;
    private final Session session;
    private final String sessionId;
    private final int maxTurns;
    private final String model;
    private final Model modelOverride;
    private final RunHooks<T> runHooks;
    private final boolean tracingEnabled;

    private RunConfig(T context, Session session, String sessionId, int maxTurns,
                      String model, Model modelOverride, RunHooks<T> runHooks, boolean tracingEnabled) {
        this.context = context;
        this.session = session;
        this.sessionId = sessionId;
        this.maxTurns = maxTurns;
        this.model = model;
        this.modelOverride = modelOverride;
        this.runHooks = runHooks;
        this.tracingEnabled = tracingEnabled;
    }

    public static <T> Builder<T> builder() { return new Builder<>(); }

    public T context() { return context; }
    public Session session() { return session; }
    public String sessionId() { return sessionId; }
    public int maxTurns() { return maxTurns; }
    public String model() { return model; }
    public Model modelOverride() { return modelOverride; }
    public RunHooks<T> runHooks() { return runHooks; }
    public boolean tracingEnabled() { return tracingEnabled; }

    public static final class Builder<T> {
        private T context;
        private Session session;
        private String sessionId;
        private int maxTurns = 10;
        private String model;
        private Model modelOverride;
        private RunHooks<T> runHooks;
        private boolean tracingEnabled = true;

        public Builder<T> context(T context) { this.context = context; return this; }
        public Builder<T> session(Session session) { this.session = session; return this; }
        public Builder<T> sessionId(String sessionId) { this.sessionId = sessionId; return this; }
        public Builder<T> maxTurns(int maxTurns) { this.maxTurns = maxTurns; return this; }
        public Builder<T> model(String model) { this.model = model; return this; }
        public Builder<T> modelOverride(Model modelOverride) { this.modelOverride = modelOverride; return this; }
        public Builder<T> runHooks(RunHooks<T> runHooks) { this.runHooks = runHooks; return this; }
        public Builder<T> tracingEnabled(boolean tracingEnabled) { this.tracingEnabled = tracingEnabled; return this; }

        public RunConfig<T> build() {
            return new RunConfig<>(context, session, sessionId, maxTurns, model, modelOverride, runHooks, tracingEnabled);
        }
    }
}
