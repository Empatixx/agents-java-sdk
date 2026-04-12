package cz.krokviak.agents.api.dto;

import cz.krokviak.agents.runner.InputItem;

import java.util.List;

/**
 * Immutable snapshot of the agent's conversation history at a point in time.
 * Obtained via {@link cz.krokviak.agents.api.AgentService#history()}.
 * Mutations to the live agent context do <i>not</i> propagate into a snapshot.
 *
 * @param items           ordered list of history entries (user, assistant, tool, system)
 * @param estimatedTokens rough token count for the whole history, useful for UI warnings
 */
public record HistorySnapshot(
    List<InputItem> items,
    int estimatedTokens
) {
    public int size() { return items.size(); }
}
