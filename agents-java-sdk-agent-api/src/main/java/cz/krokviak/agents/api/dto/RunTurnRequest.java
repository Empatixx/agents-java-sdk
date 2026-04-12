package cz.krokviak.agents.api.dto;

import java.util.List;

/**
 * Input to {@link cz.krokviak.agents.api.AgentService#runTurn(RunTurnRequest)}.
 * Carries the user message plus optional attachments that should be visible
 * to the model on the next turn.
 *
 * @param userText            the user's text message (typically the chat input)
 * @param attachedImagePaths  absolute paths of images to attach to this turn; may be empty
 * @param maxTurnsOverride    one-shot override of the agent's default max-turns ceiling;
 *                            {@code null} to use the default configured on the runner
 */
public record RunTurnRequest(
    String userText,
    List<String> attachedImagePaths,
    Integer maxTurnsOverride
) {
    /** Shorthand for a simple text-only turn with default limits. */
    public static RunTurnRequest of(String text) {
        return new RunTurnRequest(text, List.of(), null);
    }
}
