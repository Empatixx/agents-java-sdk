package cz.krokviak.agents.tool;

public interface Tool {
    String name();
    String description();
    default boolean isEnabled() { return true; }
}
