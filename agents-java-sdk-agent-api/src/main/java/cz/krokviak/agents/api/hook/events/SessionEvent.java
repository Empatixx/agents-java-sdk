package cz.krokviak.agents.api.hook.events;

/**
 * Dispatched once per session at start (SESSION_START) and end (SESSION_END).
 * Frontends use this to warm caches, pre-load plugin data on open, and to
 * flush/persist state on close.
 */
public record SessionEvent(String sessionId, int messageCount) {}
