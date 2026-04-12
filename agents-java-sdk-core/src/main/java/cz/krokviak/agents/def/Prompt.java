package cz.krokviak.agents.def;

import cz.krokviak.agents.context.RunContext;
import java.util.Map;
import java.util.function.BiFunction;

public final class Prompt {
    private final String staticText;
    private final BiFunction<RunContext<?>, Agent<?>, String> dynamicFunction;
    private final String template;
    private final Map<String, String> templateVars;

    private Prompt(String staticText, BiFunction<RunContext<?>, Agent<?>, String> dynamicFunction,
                   String template, Map<String, String> templateVars) {
        this.staticText = staticText;
        this.dynamicFunction = dynamicFunction;
        this.template = template;
        this.templateVars = templateVars;
    }

    public static Prompt of(String text) { return new Prompt(text, null, null, null); }

    public static Prompt dynamic(BiFunction<RunContext<?>, Agent<?>, String> fn) {
        return new Prompt(null, fn, null, null);
    }

    public static Prompt template(String template, Map<String, String> vars) {
        return new Prompt(null, null, template, Map.copyOf(vars));
    }

    @SuppressWarnings("unchecked")
    public String resolve(RunContext<?> ctx, Agent<?> agent) {
        if (dynamicFunction != null) return dynamicFunction.apply(ctx, agent);
        if (template != null) {
            String result = template;
            for (var entry : templateVars.entrySet()) {
                result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
            }
            return result;
        }
        return staticText != null ? staticText : "";
    }

    public String staticText() { return staticText; }
}
