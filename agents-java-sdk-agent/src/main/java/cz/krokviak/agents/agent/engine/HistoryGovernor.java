package cz.krokviak.agents.agent.engine;

import cz.krokviak.agents.agent.AgentDefaults;
import cz.krokviak.agents.api.event.EventBus;
import cz.krokviak.agents.runner.InputItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Belt-and-braces protector against unbounded history growth.
 *
 * <p>The compaction pipeline is the primary line of defence — layers 1-3
 * bring history back into budget. But if a frontend appends items faster
 * than compaction can summarise, or if compaction fails, we need a hard
 * safety net: trim oldest items when the list crosses
 * {@link AgentDefaults#HISTORY_HARD_CAP}.
 *
 * <p>Below the hard cap but above {@link AgentDefaults#HISTORY_WARN_THRESHOLD}
 * we emit a one-time warning so operators can notice degeneration.
 */
public final class HistoryGovernor {

    private static final Logger log = LoggerFactory.getLogger(HistoryGovernor.class);

    private final AtomicBoolean warned = new AtomicBoolean(false);

    /**
     * Enforce the hard cap in place. Callers pass their own event bus so
     * the warning surfaces on the UI.
     *
     * @return number of items dropped (0 if under cap)
     */
    public int enforce(List<InputItem> history, EventBus bus) {
        if (history == null) return 0;
        int size = history.size();
        if (size > AgentDefaults.HISTORY_WARN_THRESHOLD && warned.compareAndSet(false, true)) {
            log.warn("History is large ({} items). Compaction should be kicking in.", size);
            if (bus != null) {
                bus.emit(new cz.krokviak.agents.api.event.AgentEvent.ErrorOccurred(
                    "Conversation history is large (" + size + " items). If this persists, check compaction settings."));
            }
        }
        if (size <= AgentDefaults.HISTORY_HARD_CAP) return 0;

        // Drop oldest items until we're at cap. Preserve the very first item
        // (usually a SystemMessage / initial UserMessage) because cutting there
        // would orphan too much context.
        int keepAfter = 1;
        int toDrop = size - AgentDefaults.HISTORY_HARD_CAP;
        // Guard against pathological state — never drop everything.
        toDrop = Math.min(toDrop, size - keepAfter - 1);
        if (toDrop <= 0) return 0;

        synchronized (history) {
            // Remove range [1, 1+toDrop) — the window after index 0.
            history.subList(1, Math.min(1 + toDrop, history.size())).clear();
        }

        log.warn("History trimmed: dropped {} oldest items, size now {}", toDrop, history.size());
        if (bus != null) {
            bus.emit(new cz.krokviak.agents.api.event.AgentEvent.CompactionTriggered(size, history.size()));
        }
        return toDrop;
    }

    /** Visible for testing — reset the one-shot warning flag. */
    void resetWarning() { warned.set(false); }
}
