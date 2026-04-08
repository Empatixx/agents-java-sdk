package cz.krokviak.agents.cli.engine;

import cz.krokviak.agents.context.ToolContext;
import cz.krokviak.agents.tool.*;
import java.util.Map;
import java.util.function.Supplier;

public class DeferredTool implements ExecutableTool {
    private final String name;
    private final String description;
    private final String searchHint;
    private volatile ExecutableTool delegate;
    private final Supplier<ExecutableTool> loader;
    private volatile boolean loaded;

    public DeferredTool(String name, String description, String searchHint, Supplier<ExecutableTool> loader) {
        this.name = name;
        this.description = description;
        this.searchHint = searchHint;
        this.loader = loader;
    }

    @Override public String name() { return name; }
    @Override public String description() { return description; }
    public String searchHint() { return searchHint; }
    public boolean isLoaded() { return loaded; }

    public synchronized ExecutableTool load() {
        if (!loaded) { delegate = loader.get(); loaded = true; }
        return delegate;
    }

    @Override
    public ToolDefinition definition() {
        if (loaded && delegate != null) return delegate.definition();
        return new ToolDefinition(name, description, Map.of("type", "object", "properties", Map.of()));
    }

    @Override
    public ToolOutput execute(ToolArgs args, ToolContext<?> ctx) throws Exception {
        load();
        if (delegate == null) throw new IllegalStateException("Failed to load deferred tool: " + name);
        return delegate.execute(args, ctx);
    }
}
