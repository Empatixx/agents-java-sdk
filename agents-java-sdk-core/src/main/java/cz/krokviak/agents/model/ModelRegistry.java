package cz.krokviak.agents.model;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public final class ModelRegistry {
    private static final ConcurrentHashMap<String, Model> models = new ConcurrentHashMap<>();
    private static final AtomicReference<Model> defaultModel = new AtomicReference<>();

    private ModelRegistry() {}

    public static void register(String name, Model model) {
        models.put(name, model);
    }

    public static void setDefault(Model model) {
        defaultModel.set(model);
    }

    public static Model resolve(String name) {
        Model model = models.get(name);
        if (model != null) return model;

        Model fallback = defaultModel.get();
        if (fallback != null) return fallback;

        throw new IllegalStateException(
            "No model registered for '" + name + "' and no default model set. " +
            "Call ModelRegistry.setDefault() or ModelRegistry.register() first.");
    }

    public static void clear() {
        models.clear();
        defaultModel.set(null);
    }
}
