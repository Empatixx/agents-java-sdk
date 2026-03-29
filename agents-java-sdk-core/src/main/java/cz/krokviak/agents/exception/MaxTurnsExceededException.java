package cz.krokviak.agents.exception;

public class MaxTurnsExceededException extends AgentException {
    private final int maxTurns;
    public MaxTurnsExceededException(int maxTurns) {
        super("Agent exceeded maximum turns: " + maxTurns);
        this.maxTurns = maxTurns;
    }
    public int maxTurns() { return maxTurns; }
}
