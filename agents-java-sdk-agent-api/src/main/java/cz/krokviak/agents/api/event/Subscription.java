package cz.krokviak.agents.api.event;

/** Handle for cancelling an event subscription. */
public interface Subscription {
    void unsubscribe();
}
