package cz.krokviak.agents.cli.style;

import java.util.List;
import java.util.Optional;

/**
 * Holds the loaded {@link OutputStyle}s and tracks which one is currently active.
 * Frontends apply the active style by pushing its {@link OutputStyle#systemPrompt()}
 * into {@code AgentContext.setSystemPromptSuffix(...)}.
 */
public final class OutputStyleRegistry {

    private final List<OutputStyle> styles;
    private volatile String activeName;

    public OutputStyleRegistry(List<OutputStyle> styles) {
        this.styles = List.copyOf(styles);
    }

    public List<OutputStyle> all() { return styles; }

    public Optional<OutputStyle> find(String name) {
        if (name == null) return Optional.empty();
        return styles.stream().filter(s -> s.name().equals(name)).findFirst();
    }

    public String activeName() { return activeName; }

    public Optional<OutputStyle> active() { return find(activeName); }

    public void activate(String name) { this.activeName = name; }
    public void clear() { this.activeName = null; }
}
