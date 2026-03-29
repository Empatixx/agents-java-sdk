package cz.krokviak.agents.guardrail;

import cz.krokviak.agents.context.RunContext;
import java.util.function.BiFunction;

public final class InputGuardrail<T> {
    private final String name;
    private final BiFunction<RunContext<T>, GuardrailInputData, GuardrailResult> check;

    private InputGuardrail(String name, BiFunction<RunContext<T>, GuardrailInputData, GuardrailResult> check) {
        this.name = name;
        this.check = check;
    }

    public static <T> InputGuardrail<T> of(String name, BiFunction<RunContext<T>, GuardrailInputData, GuardrailResult> check) {
        return new InputGuardrail<>(name, check);
    }

    public String name() { return name; }

    public GuardrailResult execute(RunContext<T> ctx, GuardrailInputData input) {
        return check.apply(ctx, input);
    }
}
