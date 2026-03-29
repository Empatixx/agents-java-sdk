package cz.krokviak.agents.context;

public class ToolContext<T> {
    private final RunContext<T> runContext;
    private final String toolCallId;

    public ToolContext(RunContext<T> runContext, String toolCallId) {
        this.runContext = runContext;
        this.toolCallId = toolCallId;
    }
    public T context() { return runContext.context(); }
    public String toolCallId() { return toolCallId; }
    public RunContext<T> runContext() { return runContext; }
}
