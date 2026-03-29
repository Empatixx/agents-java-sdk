package cz.krokviak.agents.guardrail;

import cz.krokviak.agents.context.RunContext;
import java.util.Map;
import java.util.function.BiFunction;

public final class ToolInputGuardrail<T> {
    private final String name;
    private final BiFunction<RunContext<T>, ToolCallData, GuardrailResult> check;

    private ToolInputGuardrail(String name, BiFunction<RunContext<T>, ToolCallData, GuardrailResult> check) {
        this.name = name;
        this.check = check;
    }

    public static <T> ToolInputGuardrail<T> of(String name, BiFunction<RunContext<T>, ToolCallData, GuardrailResult> check) {
        return new ToolInputGuardrail<>(name, check);
    }

    public String name() { return name; }
    public GuardrailResult execute(RunContext<T> ctx, ToolCallData data) { return check.apply(ctx, data); }
}
