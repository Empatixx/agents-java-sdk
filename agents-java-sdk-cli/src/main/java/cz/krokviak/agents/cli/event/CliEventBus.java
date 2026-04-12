package cz.krokviak.agents.cli.event;

import cz.krokviak.agents.api.event.AgentEvent;
import cz.krokviak.agents.api.event.EventBus;
import cz.krokviak.agents.api.event.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Synchronous event bus for agent lifecycle events.
 * Implements the UI-agnostic {@link EventBus} contract from agent-api while
 * preserving the historical {@code CliEventBus} surface used across the CLI.
 * Listeners are called in registration order on the emitting thread.
 */
public final class CliEventBus implements EventBus {
    private static final Logger log = LoggerFactory.getLogger(CliEventBus.class);

    private final List<Consumer<AgentEvent>> listeners = new CopyOnWriteArrayList<>();

    @Override
    public Subscription subscribe(Consumer<AgentEvent> listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends AgentEvent> Subscription on(Class<T> eventType, Consumer<T> handler) {
        Consumer<AgentEvent> wrapper = event -> {
            if (eventType.isInstance(event)) handler.accept((T) event);
        };
        listeners.add(wrapper);
        return () -> listeners.remove(wrapper);
    }

    @Override
    public void emit(AgentEvent event) {
        for (var listener : listeners) {
            try {
                listener.accept(event);
            } catch (Exception e) {
                log.warn("Event listener failed for {}: {}", event.getClass().getSimpleName(), e.getMessage());
            }
        }
    }
}
