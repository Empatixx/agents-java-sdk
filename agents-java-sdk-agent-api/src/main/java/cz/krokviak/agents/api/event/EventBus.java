package cz.krokviak.agents.api.event;

import java.util.function.Consumer;

/**
 * Publish/subscribe bus for {@link AgentEvent}s. Implementations define
 * threading semantics; the default contract is synchronous dispatch on the
 * emitting thread.
 */
public interface EventBus {

    /** Subscribe to all events. */
    Subscription subscribe(Consumer<AgentEvent> listener);

    /** Subscribe to events of a specific type. */
    <T extends AgentEvent> Subscription on(Class<T> type, Consumer<T> handler);

    /** Emit an event to all matching listeners. */
    void emit(AgentEvent event);
}
