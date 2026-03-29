package cz.krokviak.agents.exception;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExceptionHierarchyTest {
    @Test
    void allExceptionsExtendAgentException() {
        assertInstanceOf(AgentException.class, new MaxTurnsExceededException(10));
        assertInstanceOf(AgentException.class, new ModelBehaviorException("bad"));
        assertInstanceOf(AgentException.class, new ToolExecutionException("tool", new RuntimeException()));
        assertInstanceOf(AgentException.class, new ToolTimeoutException("tool", 5000));
        assertInstanceOf(AgentException.class, new InputGuardrailTrippedException("guard", "reason"));
        assertInstanceOf(AgentException.class, new OutputGuardrailTrippedException("guard", "reason"));
        assertInstanceOf(AgentException.class, new HandoffException("err"));
        assertInstanceOf(AgentException.class, new SessionException("err"));
    }
    @Test
    void allExceptionsAreUnchecked() {
        assertInstanceOf(RuntimeException.class, new AgentException("test"));
    }
    @Test
    void toolTimeoutExtendsToolExecution() {
        assertInstanceOf(ToolExecutionException.class, new ToolTimeoutException("tool", 5000));
    }
    @Test
    void maxTurnsExceededCarriesTurnCount() {
        var ex = new MaxTurnsExceededException(10);
        assertEquals(10, ex.maxTurns());
        assertTrue(ex.getMessage().contains("10"));
    }
    @Test
    void guardrailExceptionsCarryDetails() {
        var input = new InputGuardrailTrippedException("pii_check", "PII detected");
        assertEquals("pii_check", input.guardrailName());
        assertEquals("PII detected", input.reason());
        var output = new OutputGuardrailTrippedException("toxicity", "Toxic content");
        assertEquals("toxicity", output.guardrailName());
        assertEquals("Toxic content", output.reason());
    }
}
