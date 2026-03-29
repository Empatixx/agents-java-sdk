package cz.krokviak.agents.model;

public record Usage(int inputTokens, int outputTokens) {
    public int totalTokens() { return inputTokens + outputTokens; }
    public Usage add(Usage other) { return new Usage(this.inputTokens + other.inputTokens, this.outputTokens + other.outputTokens); }
    public static Usage zero() { return new Usage(0, 0); }
}
