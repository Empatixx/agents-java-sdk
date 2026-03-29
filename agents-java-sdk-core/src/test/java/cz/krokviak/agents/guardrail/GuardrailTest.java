package cz.krokviak.agents.guardrail;

import cz.krokviak.agents.context.RunContext;
import cz.krokviak.agents.runner.InputItem;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class GuardrailTest {

    @Test
    void inputGuardrailPasses() {
        InputGuardrail<Void> guard = InputGuardrail.of("safe", (ctx, input) ->
            GuardrailResult.pass()
        );

        var data = new GuardrailInputData(List.of(new InputItem.UserMessage("hello")));
        var result = guard.execute(new RunContext<>(null), data);
        assertFalse(result.tripped());
    }

    @Test
    void inputGuardrailTrips() {
        InputGuardrail<Void> guard = InputGuardrail.of("pii_check", (ctx, input) -> {
            if (input.text().contains("SSN")) {
                return GuardrailResult.tripwire("PII detected");
            }
            return GuardrailResult.pass();
        });

        var data = new GuardrailInputData(List.of(new InputItem.UserMessage("My SSN is 123")));
        var result = guard.execute(new RunContext<>(null), data);
        assertTrue(result.tripped());
        assertEquals("PII detected", result.reason());
    }

    @Test
    void outputGuardrailPasses() {
        OutputGuardrail<Void> guard = OutputGuardrail.of("safe", (ctx, output) ->
            GuardrailResult.pass()
        );

        var result = guard.execute(new RunContext<>(null), "clean output");
        assertFalse(result.tripped());
    }

    @Test
    void outputGuardrailTrips() {
        OutputGuardrail<Void> guard = OutputGuardrail.of("length", (ctx, output) -> {
            if (output.length() > 10) {
                return GuardrailResult.tripwire("Too long");
            }
            return GuardrailResult.pass();
        });

        var result = guard.execute(new RunContext<>(null), "this is a very long output string");
        assertTrue(result.tripped());
    }

    @Test
    void guardrailInputDataExtractsText() {
        var data = new GuardrailInputData(List.of(
            new InputItem.UserMessage("Hello "),
            new InputItem.UserMessage("World")
        ));
        assertEquals("Hello World", data.text());
    }
}
