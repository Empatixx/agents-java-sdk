package cz.krokviak.agents.exception;

public class ToolExecutionException extends AgentException {
    private final String toolName;
    public ToolExecutionException(String toolName, Throwable cause) {
        super("Tool execution failed: " + toolName, cause);
        this.toolName = toolName;
    }
    public ToolExecutionException(String toolName, String message) {
        super("Tool execution failed [" + toolName + "]: " + message);
        this.toolName = toolName;
    }
    public String toolName() { return toolName; }
}
