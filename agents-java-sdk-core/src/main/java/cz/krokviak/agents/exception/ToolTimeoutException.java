package cz.krokviak.agents.exception;

public class ToolTimeoutException extends ToolExecutionException {
    private final long timeoutMs;
    public ToolTimeoutException(String toolName, long timeoutMs) {
        super(toolName, "Timed out after " + timeoutMs + "ms");
        this.timeoutMs = timeoutMs;
    }
    public long timeoutMs() { return timeoutMs; }
}
