package cz.krokviak.agents.api.hook.events;

import cz.krokviak.agents.runner.InputItem;

import java.util.List;

/**
 * Dispatched once at the end of a {@code runTurn()}, after all tool batches
 * have resolved. A hook returning {@code HookResult.Block} prevents further
 * continuation from the engine (the run stops even if {@code maxTurns}
 * remains).
 */
public record PostTurnEvent(
    String assistantOutput,
    List<InputItem.ToolCall> toolsUsed,
    int turnsElapsed
) {}
