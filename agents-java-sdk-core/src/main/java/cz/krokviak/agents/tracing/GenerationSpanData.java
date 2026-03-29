package cz.krokviak.agents.tracing;

public record GenerationSpanData(
    String model,
    int inputTokens,
    int outputTokens,
    String responseId
) {
    public GenerationSpanData(String model) {
        this(model, 0, 0, null);
    }
}
