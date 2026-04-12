package cz.krokviak.agents.model;

/**
 * Extended-thinking configuration for Anthropic models that support it
 * (claude-opus-4, claude-sonnet-4+). Enables a visible "thinking" phase
 * where the model reasons before producing its final answer.
 *
 * @param enabled       Whether to request thinking from the model.
 * @param budgetTokens  Soft upper bound on thinking tokens. Ignored when disabled.
 */
public record ThinkingConfig(boolean enabled, int budgetTokens) {
    public static final int DEFAULT_BUDGET = 4096;

    public static ThinkingConfig off() { return new ThinkingConfig(false, 0); }
    public static ThinkingConfig on() { return new ThinkingConfig(true, DEFAULT_BUDGET); }
    public static ThinkingConfig on(int budgetTokens) { return new ThinkingConfig(true, budgetTokens); }
}
