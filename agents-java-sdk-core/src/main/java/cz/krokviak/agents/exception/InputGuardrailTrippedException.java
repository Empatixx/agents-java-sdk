package cz.krokviak.agents.exception;

public class InputGuardrailTrippedException extends AgentException {
    private final String guardrailName;
    private final String reason;
    public InputGuardrailTrippedException(String guardrailName, String reason) {
        super("Input guardrail tripped [" + guardrailName + "]: " + reason);
        this.guardrailName = guardrailName;
        this.reason = reason;
    }
    public String guardrailName() { return guardrailName; }
    public String reason() { return reason; }
}
