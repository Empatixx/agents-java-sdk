package cz.krokviak.agents.api.hook.events;

/**
 * Dispatched immediately before (PRE_COMPACT) and after (POST_COMPACT) the
 * compaction pipeline runs. For PRE, {@link #sizeAfter()} is {@code -1}.
 * {@link #trigger()} is a short tag describing why compaction fired
 * ({@code "budget"}, {@code "user"}, {@code "context-too-long"}).
 */
public record CompactEvent(int sizeBefore, int sizeAfter, String trigger) {}
