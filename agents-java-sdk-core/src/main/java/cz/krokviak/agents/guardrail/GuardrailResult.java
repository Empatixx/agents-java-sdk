package cz.krokviak.agents.guardrail;

public record GuardrailResult(boolean tripped, String reason) {
    public static GuardrailResult pass() {
        return new GuardrailResult(false, null);
    }

    public static GuardrailResult tripwire(String reason) {
        return new GuardrailResult(true, reason);
    }
}
