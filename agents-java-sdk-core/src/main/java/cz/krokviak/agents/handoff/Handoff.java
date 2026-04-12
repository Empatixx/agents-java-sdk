package cz.krokviak.agents.handoff;

import cz.krokviak.agents.def.Agent;
import cz.krokviak.agents.context.RunContext;
import cz.krokviak.agents.runner.InputItem;

import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

public final class Handoff<TContext> {
    private final Agent<TContext> agent;
    private final String description;
    private final Consumer<RunContext<TContext>> onHandoff;
    private final UnaryOperator<List<InputItem>> inputFilter;
    private final BiPredicate<RunContext<TContext>, Agent<TContext>> enabled;

    Handoff(Agent<TContext> agent, String description,
            Consumer<RunContext<TContext>> onHandoff,
            UnaryOperator<List<InputItem>> inputFilter,
            BiPredicate<RunContext<TContext>, Agent<TContext>> enabled) {
        this.agent = agent;
        this.description = description;
        this.onHandoff = onHandoff;
        this.inputFilter = inputFilter;
        this.enabled = enabled;
    }

    public static <T> HandoffBuilder<T> to(Agent<T> agent) {
        return new HandoffBuilder<>(agent);
    }

    public Agent<TContext> agent() { return agent; }
    public String description() { return description; }
    public Consumer<RunContext<TContext>> onHandoff() { return onHandoff; }
    public UnaryOperator<List<InputItem>> inputFilter() { return inputFilter; }
    public BiPredicate<RunContext<TContext>, Agent<TContext>> enabled() { return enabled; }

    public String toolName() {
        return "transfer_to_" + agent.name();
    }

    public boolean isEnabled(RunContext<TContext> ctx) {
        return enabled == null || enabled.test(ctx, agent);
    }
}
