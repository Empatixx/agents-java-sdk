package cz.krokviak.agents.guardrail;

import java.util.List;

public record GuardrailResults(
    List<GuardrailResult> inputResults,
    List<GuardrailResult> outputResults
) {
    public static GuardrailResults empty() {
        return new GuardrailResults(List.of(), List.of());
    }
}
