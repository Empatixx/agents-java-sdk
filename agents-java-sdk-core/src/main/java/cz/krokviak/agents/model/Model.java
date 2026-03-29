package cz.krokviak.agents.model;

public interface Model {
    ModelResponse call(LlmContext context, ModelSettings settings);
    ModelResponseStream callStreamed(LlmContext context, ModelSettings settings);
}
