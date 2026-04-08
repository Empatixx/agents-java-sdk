package cz.krokviak.agents.cli.engine;

public class TokenBudget {
    private final int maxBudget;
    private int totalTokensUsed;
    private int turnCount;
    private int continuationCount;

    public TokenBudget(int maxBudget) { this.maxBudget = maxBudget; }

    public void recordTurn(int inputTokens, int outputTokens) {
        totalTokensUsed += inputTokens + outputTokens;
        turnCount++;
        if (turnCount > 1 && outputTokens < 500) continuationCount++;
        else continuationCount = 0;
    }

    public boolean isOverBudget() { return totalTokensUsed >= maxBudget; }
    public boolean isDiminishingReturns() { return continuationCount >= 3; }
    public int remaining() { return Math.max(0, maxBudget - totalTokensUsed); }
    public int totalUsed() { return totalTokensUsed; }
    public int turnCount() { return turnCount; }
    public String format() { return String.format("[budget: %,d / %,d tokens, %d turns]", totalTokensUsed, maxBudget, turnCount); }
}
