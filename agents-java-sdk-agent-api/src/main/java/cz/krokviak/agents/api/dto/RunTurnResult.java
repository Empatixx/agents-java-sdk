package cz.krokviak.agents.api.dto;

/**
 * Outcome of {@link cz.krokviak.agents.api.AgentService#runTurn(RunTurnRequest)}.
 * Details arrive on the event bus during the turn; this record summarises the
 * aggregate once it finishes.
 *
 * @param finalOutput  the last assistant-text chunk; {@code null} if the turn
 *                     ended without a text reply (tool-only turn)
 * @param turns        number of model↔tool round-trips consumed by this call
 * @param inputTokens  prompt tokens billed for this turn
 * @param outputTokens completion tokens billed for this turn
 * @param interrupted  {@code true} if the turn was stopped early via
 *                     {@link cz.krokviak.agents.api.AgentService#cancelTurn()}
 *                     or a POST_TURN hook {@code Block}; otherwise {@code false}
 */
public record RunTurnResult(
    String finalOutput,
    int turns,
    long inputTokens,
    long outputTokens,
    boolean interrupted
) {}
