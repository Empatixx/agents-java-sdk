package cz.krokviak.agents.handoff;

import cz.krokviak.agents.agent.Agent;
import cz.krokviak.agents.context.RunContext;
import cz.krokviak.agents.runner.InputItem;

import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

public final class HandoffBuilder<TContext> {
    private final Agent<TContext> agent;
    private String description;
    private Consumer<RunContext<TContext>> onHandoff;
    private UnaryOperator<List<InputItem>> inputFilter;
    private BiPredicate<RunContext<TContext>, Agent<TContext>> enabled;

    HandoffBuilder(Agent<TContext> agent) {
        this.agent = agent;
    }

    public HandoffBuilder<TContext> description(String description) {
        this.description = description;
        return this;
    }

    public HandoffBuilder<TContext> onHandoff(Consumer<RunContext<TContext>> onHandoff) {
        this.onHandoff = onHandoff;
        return this;
    }

    public HandoffBuilder<TContext> inputFilter(UnaryOperator<List<InputItem>> inputFilter) {
        this.inputFilter = inputFilter;
        return this;
    }

    public HandoffBuilder<TContext> enabled(BiPredicate<RunContext<TContext>, Agent<TContext>> enabled) {
        this.enabled = enabled;
        return this;
    }

    public Handoff<TContext> build() {
        return new Handoff<>(agent, description, onHandoff, inputFilter, enabled);
    }
}
