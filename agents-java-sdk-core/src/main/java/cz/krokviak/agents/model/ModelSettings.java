package cz.krokviak.agents.model;

public record ModelSettings(
    Double temperature,
    Double topP,
    Double frequencyPenalty,
    Double presencePenalty,
    Integer maxTokens,
    Boolean parallelToolCalls,
    String truncation
) {
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private Double temperature;
        private Double topP;
        private Double frequencyPenalty;
        private Double presencePenalty;
        private Integer maxTokens;
        private Boolean parallelToolCalls;
        private String truncation;

        public Builder temperature(double v) { this.temperature = v; return this; }
        public Builder topP(double v) { this.topP = v; return this; }
        public Builder frequencyPenalty(double v) { this.frequencyPenalty = v; return this; }
        public Builder presencePenalty(double v) { this.presencePenalty = v; return this; }
        public Builder maxTokens(int v) { this.maxTokens = v; return this; }
        public Builder parallelToolCalls(boolean v) { this.parallelToolCalls = v; return this; }
        public Builder truncation(String v) { this.truncation = v; return this; }
        public ModelSettings build() {
            return new ModelSettings(temperature, topP, frequencyPenalty, presencePenalty,
                maxTokens, parallelToolCalls, truncation);
        }
    }
}
