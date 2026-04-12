package cz.krokviak.agents.agent.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TokenBudgetTest {

    @Test
    void notOverBudgetInitially() {
        var budget = new TokenBudget(10_000);
        assertFalse(budget.isOverBudget());
        assertEquals(0, budget.totalUsed());
    }

    @Test
    void overBudgetWhenExceeded() {
        var budget = new TokenBudget(1000);
        budget.recordTurn(600, 500);
        assertTrue(budget.isOverBudget());
    }

    @Test
    void notOverBudgetWhenUnder() {
        var budget = new TokenBudget(10_000);
        budget.recordTurn(2000, 500);
        budget.recordTurn(3000, 400);
        assertFalse(budget.isOverBudget());
        assertEquals(5900, budget.totalUsed());
    }

    @Test
    void diminishingReturnsAfterFiveLowOutputTurns() {
        var budget = new TokenBudget(200_000);
        budget.recordTurn(1000, 500); // turn 1: normal, resets counter
        budget.recordTurn(1000, 50);  // turn 2: low output
        budget.recordTurn(1000, 30);  // turn 3: low output
        budget.recordTurn(1000, 10);  // turn 4: low output
        budget.recordTurn(1000, 5);   // turn 5: low output
        assertFalse(budget.isDiminishingReturns()); // only 4 consecutive
        budget.recordTurn(1000, 20);  // turn 6: low output
        assertTrue(budget.isDiminishingReturns()); // 5 consecutive
    }

    @Test
    void diminishingReturnsResetsOnNormalOutput() {
        var budget = new TokenBudget(200_000);
        budget.recordTurn(1000, 500); // normal
        budget.recordTurn(1000, 50);  // low
        budget.recordTurn(1000, 50);  // low
        budget.recordTurn(1000, 50);  // low
        budget.recordTurn(1000, 200); // normal — resets counter
        budget.recordTurn(1000, 50);  // low
        assertFalse(budget.isDiminishingReturns());
    }

    @Test
    void remainingTokens() {
        var budget = new TokenBudget(5000);
        budget.recordTurn(1000, 500);
        assertEquals(3500, budget.remaining());
    }

    @Test
    void turnCountTracked() {
        var budget = new TokenBudget(100_000);
        budget.recordTurn(100, 100);
        budget.recordTurn(100, 100);
        budget.recordTurn(100, 100);
        assertEquals(3, budget.turnCount());
    }
}
