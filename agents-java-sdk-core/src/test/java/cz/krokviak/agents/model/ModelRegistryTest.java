package cz.krokviak.agents.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ModelRegistryTest {

    @BeforeEach
    void reset() {
        ModelRegistry.clear();
    }

    @Test
    void registerAndResolve() {
        Model mock = new StubModel("test");
        ModelRegistry.register("gpt-test", mock);
        assertSame(mock, ModelRegistry.resolve("gpt-test"));
    }

    @Test
    void resolveReturnsDefaultWhenNotFound() {
        Model defaultModel = new StubModel("default");
        ModelRegistry.setDefault(defaultModel);
        assertSame(defaultModel, ModelRegistry.resolve("unknown-model"));
    }

    @Test
    void resolveThrowsWhenNoDefaultAndNotFound() {
        assertThrows(IllegalStateException.class, () -> ModelRegistry.resolve("missing"));
    }

    @Test
    void setDefaultModel() {
        Model model = new StubModel("default");
        ModelRegistry.setDefault(model);
        assertSame(model, ModelRegistry.resolve("anything"));
    }

    record StubModel(String id) implements Model {
        @Override
        public ModelResponse call(LlmContext context, ModelSettings settings) {
            return new ModelResponse("stub", java.util.List.of(), Usage.zero());
        }

        @Override
        public ModelResponseStream callStreamed(LlmContext context, ModelSettings settings) {
            return null;
        }
    }
}
