package cz.krokviak.agents.guardrail;

import cz.krokviak.agents.context.RunContext;
import cz.krokviak.agents.tool.ToolOutput;
import java.util.function.BiFunction;

public final class ToolOutputGuardrail<T> {
    private final String name;
    private final BiFunction<RunContext<T>, ToolOutputData, GuardrailResult> check;

    private ToolOutputGuardrail(String name, BiFunction<RunContext<T>, ToolOutputData, GuardrailResult> check) {
        this.name = name;
        this.check = check;
    }

    public static <T> ToolOutputGuardrail<T> of(String name, BiFunction<RunContext<T>, ToolOutputData, GuardrailResult> check) {
        return new ToolOutputGuardrail<>(name, check);
    }

    public String name() { return name; }
    public GuardrailResult execute(RunContext<T> ctx, ToolOutputData data) { return check.apply(ctx, data); }
}
