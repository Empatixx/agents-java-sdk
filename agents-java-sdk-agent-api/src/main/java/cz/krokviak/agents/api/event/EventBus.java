package cz.krokviak.agents.api.event;

import java.util.function.Consumer;

/**
 * Publish / subscribe bus for {@link AgentEvent}s emitted during a run.
 * Frontends subscribe ({@link #subscribe(Consumer)} / {@link #on(Class, Consumer)})
 * to render output; agent internals publish ({@link #emit(AgentEvent)}).
 *
 * <p>Default contract is synchronous dispatch on the emitting thread.
 * Implementations are expected to tolerate listener exceptions without
 * breaking the emit loop (log + continue).
 */
public interface EventBus {

    /** Subscribe to every event on the bus. Returns a {@link Subscription} for cancellation. */
    Subscription subscribe(Consumer<AgentEvent> listener);

    /** Subscribe only to events of type {@code T}. Returns a {@link Subscription} for cancellation. */
    <T extends AgentEvent> Subscription on(Class<T> type, Consumer<T> handler);

    /** Emit an event to every matching listener. */
    void emit(AgentEvent event);
}
