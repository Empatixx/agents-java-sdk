package cz.krokviak.agents.exception;

public class HandoffException extends AgentException {
    public HandoffException(String message) { super(message); }
    public HandoffException(String message, Throwable cause) { super(message, cause); }
}
