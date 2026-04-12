package cz.krokviak.agents.api.dto;

import cz.krokviak.agents.runner.InputItem;

import java.util.List;

/** Immutable snapshot of the agent's conversation history at a point in time. */
public record HistorySnapshot(
    List<InputItem> items,
    int estimatedTokens
) {
    public int size() { return items.size(); }
}
