package cz.krokviak.agents.tool;

import java.util.Map;

public record ToolArgs(Map<String, Object> raw) {

    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> type) {
        Object value = raw.get(key);
        if (value == null) return null;
        return (T) value;
    }

    public <T> T getOrDefault(String key, Class<T> type, T defaultValue) {
        T value = get(key, type);
        return value != null ? value : defaultValue;
    }
}
