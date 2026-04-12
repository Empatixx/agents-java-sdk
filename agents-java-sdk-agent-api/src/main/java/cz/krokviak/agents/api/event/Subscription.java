package cz.krokviak.agents.api.event;

/**
 * Handle returned by {@link EventBus#subscribe} /
 * {@link EventBus#on} so the listener can later stop receiving events.
 * Typically used at frontend shutdown or when a component that owned the
 * listener is disposed.
 */
public interface Subscription {
    /** Remove the listener. Idempotent; safe to call multiple times. */
    void unsubscribe();
}
