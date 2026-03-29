package cz.krokviak.agents.exception;

public class OutputGuardrailTrippedException extends AgentException {
    private final String guardrailName;
    private final String reason;
    public OutputGuardrailTrippedException(String guardrailName, String reason) {
        super("Output guardrail tripped [" + guardrailName + "]: " + reason);
        this.guardrailName = guardrailName;
        this.reason = reason;
    }
    public String guardrailName() { return guardrailName; }
    public String reason() { return reason; }
}
