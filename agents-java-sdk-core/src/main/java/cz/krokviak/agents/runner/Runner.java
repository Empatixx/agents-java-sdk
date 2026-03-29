package cz.krokviak.agents.runner;

import cz.krokviak.agents.agent.Agent;
import cz.krokviak.agents.context.RunContext;
import cz.krokviak.agents.model.Model;
import cz.krokviak.agents.streaming.EventStream;
import cz.krokviak.agents.streaming.StreamEvent;
import cz.krokviak.agents.tracing.Span;
import cz.krokviak.agents.tracing.Tracing;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.LinkedBlockingQueue;

public final class Runner {
    private final Model defaultModel;

    private Runner(Model defaultModel) {
        this.defaultModel = Objects.requireNonNull(defaultModel, "defaultModel must not be null");
    }

    public static Runner of(Model defaultModel) {
        return new Runner(defaultModel);
    }

    public <T> RunResult<T> run(Agent<T> agent, String input) {
        return run(agent, input, RunConfig.<T>builder().build());
    }

    public <T> RunResult<T> run(Agent<T> agent, String input, RunConfig<T> config) {
        List<InputItem> items = new ArrayList<>();
        items.add(new InputItem.UserMessage(input));
        return run(agent, items, config);
    }

    public <T> RunResult<T> run(Agent<T> agent, List<InputItem> input, RunConfig<T> config) {
        RunContext<T> ctx = new RunContext<>(config.context());
        Model model = resolveModel(agent, config);

        // Load session history
        List<InputItem> allInput = new ArrayList<>();
        if (config.session() != null && config.sessionId() != null) {
            allInput.addAll(config.session().getHistory(config.sessionId()));
        }
        allInput.addAll(input);

        RunResult<T> result;
        try (Span span = Tracing.span("agent-run")) {
            span.setAttribute("agent", agent.name());
            result = AgentLoop.run(agent, allInput, ctx, model, config.maxTurns(), config);
        }

        // Save to session
        if (config.session() != null && config.sessionId() != null) {
            config.session().save(config.sessionId(), result.newItems());
        }

        Tracing.flush();
        return result;
    }

    public <T> RunResult<T> run(Agent<T> agent, RunState state) {
        return run(agent, state.messages(), RunConfig.<T>builder().build());
    }

    public <T> EventStream<T> runStreamed(Agent<T> agent, String input) {
        return runStreamed(agent, input, RunConfig.<T>builder().build());
    }

    public <T> EventStream<T> runStreamed(Agent<T> agent, String input, RunConfig<T> config) {
        var queue = new LinkedBlockingQueue<StreamEvent<T>>();
        var stream = new EventStream<>(queue);

        Thread.startVirtualThread(() -> {
            try {
                RunResult<T> result = run(agent, input, config);
                // Emit items as events
                for (RunItem item : result.newItems()) {
                    switch (item) {
                        case RunItem.MessageOutput msg ->
                            queue.put(new StreamEvent.TextDeltaEvent<>(msg.content()));
                        case RunItem.ToolCallItem call ->
                            queue.put(new StreamEvent.ToolCallEvent<>(call.toolName(), call.arguments()));
                        case RunItem.ToolOutputItem out ->
                            queue.put(new StreamEvent.ToolResultEvent<>(out.toolName(), out.output()));
                        case RunItem.HandoffItem h ->
                            queue.put(new StreamEvent.AgentUpdatedEvent<>(result.lastAgent()));
                    }
                }
                queue.put(new StreamEvent.CompletedEvent<>(result));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        return stream;
    }

    private <T> Model resolveModel(Agent<T> agent, RunConfig<T> config) {
        if (config.modelOverride() != null) return config.modelOverride();
        return defaultModel;
    }
}
