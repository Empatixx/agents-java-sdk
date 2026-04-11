package cz.krokviak.agents.exception;

public class ContextTooLongException extends AgentException {
    public ContextTooLongException(String message) { super(message); }
    public ContextTooLongException(String message, Throwable cause) { super(message, cause); }
}
