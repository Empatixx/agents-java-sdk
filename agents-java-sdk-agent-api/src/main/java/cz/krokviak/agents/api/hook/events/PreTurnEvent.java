package cz.krokviak.agents.api.hook.events;

import java.util.List;

/**
 * Dispatched once at the start of {@code AgentRunner.run(userInput)} before
 * the model is called. Hooks may read {@link #userInput()} and append
 * additional system-prompt snippets into {@link #additionalContext()} —
 * the engine concatenates those lines into the next turn's effective
 * system prompt. The list is a mutable {@link java.util.ArrayList} owned
 * by the runner; hooks simply call {@code event.additionalContext().add(...)}.
 */
public record PreTurnEvent(String userInput, List<String> additionalContext) {}
