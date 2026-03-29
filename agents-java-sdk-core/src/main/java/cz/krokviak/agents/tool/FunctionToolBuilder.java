package cz.krokviak.agents.tool;

import cz.krokviak.agents.context.ToolContext;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Predicate;

public final class FunctionToolBuilder {
    private final String name;
    private String description = "";
    private final List<FunctionToolImpl.ParamInfo> params = new ArrayList<>();
    private BiFunction<ToolArgs, ToolContext<?>, ToolOutput> handler;
    private Predicate<Void> enabledPredicate;
    private BiFunction<String, Exception, ToolOutput> failureErrorFunction;

    FunctionToolBuilder(String name) {
        this.name = name;
    }

    public FunctionToolBuilder description(String description) {
        this.description = description;
        return this;
    }

    public FunctionToolBuilder param(String name, Class<?> type, String description) {
        params.add(new FunctionToolImpl.ParamInfo(name, description, type));
        return this;
    }

    public FunctionToolBuilder handler(BiFunction<ToolArgs, ToolContext<?>, ToolOutput> handler) {
        this.handler = handler;
        return this;
    }

    public FunctionToolBuilder isEnabled(Predicate<Void> predicate) {
        this.enabledPredicate = predicate;
        return this;
    }

    public FunctionToolBuilder failureErrorFunction(BiFunction<String, Exception, ToolOutput> failureErrorFunction) {
        this.failureErrorFunction = failureErrorFunction;
        return this;
    }

    public FunctionToolImpl build() {
        if (handler == null) throw new IllegalStateException("handler is required");
        return new FunctionToolImpl(name, description, List.copyOf(params), handler, enabledPredicate, failureErrorFunction);
    }
}
