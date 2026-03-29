package cz.krokviak.agents.guardrail;

import cz.krokviak.agents.context.RunContext;
import java.util.function.BiFunction;

public final class OutputGuardrail<T> {
    private final String name;
    private final BiFunction<RunContext<T>, String, GuardrailResult> check;

    private OutputGuardrail(String name, BiFunction<RunContext<T>, String, GuardrailResult> check) {
        this.name = name;
        this.check = check;
    }

    public static <T> OutputGuardrail<T> of(String name, BiFunction<RunContext<T>, String, GuardrailResult> check) {
        return new OutputGuardrail<>(name, check);
    }

    public String name() { return name; }

    public GuardrailResult execute(RunContext<T> ctx, String output) {
        return check.apply(ctx, output);
    }
}
