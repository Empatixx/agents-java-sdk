package cz.krokviak.agents.exception;

public class SessionException extends AgentException {
    public SessionException(String message) { super(message); }
    public SessionException(String message, Throwable cause) { super(message, cause); }
}
