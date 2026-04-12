package cz.krokviak.agents.agent.engine;

public class TokenBudget {
    private int maxBudget;
    private int totalTokensUsed;
    private int turnCount;
    private int continuationCount;

    public TokenBudget(int maxBudget) { this.maxBudget = maxBudget; }

    public void recordTurn(int inputTokens, int outputTokens) {
        totalTokensUsed += inputTokens + outputTokens;
        turnCount++;
        if (turnCount > 1 && outputTokens < 100) continuationCount++;
        else continuationCount = 0;
    }

    public boolean isOverBudget() { return totalTokensUsed >= maxBudget; }
    public boolean isDiminishingReturns() { return continuationCount >= 5; }
    public int remaining() { return Math.max(0, maxBudget - totalTokensUsed); }
    public int totalUsed() { return totalTokensUsed; }
    public int turnCount() { return turnCount; }
    public void extend(int multiplier) { maxBudget *= multiplier; }
    public int maxBudget() { return maxBudget; }
    public String format() { return String.format("[budget: %,d / %,d tokens, %d turns]", totalTokensUsed, maxBudget, turnCount); }
}
