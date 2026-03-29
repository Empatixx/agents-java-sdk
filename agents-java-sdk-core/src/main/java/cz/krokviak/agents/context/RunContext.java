package cz.krokviak.agents.context;

import cz.krokviak.agents.model.Usage;

public class RunContext<T> {
    private final T context;
    private Usage usage;

    public RunContext(T context) {
        this.context = context;
        this.usage = Usage.zero();
    }
    public T context() { return context; }
    public Usage usage() { return usage; }
    public synchronized void addUsage(Usage additional) { this.usage = this.usage.add(additional); }
}
