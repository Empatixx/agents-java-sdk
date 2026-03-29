package cz.krokviak.agents.agent;

import cz.krokviak.agents.context.RunContext;
import cz.krokviak.agents.guardrail.InputGuardrail;
import cz.krokviak.agents.guardrail.OutputGuardrail;
import cz.krokviak.agents.handoff.Handoff;
import cz.krokviak.agents.hook.AgentHooks;
import cz.krokviak.agents.model.ModelSettings;
import cz.krokviak.agents.tool.AgentTool;
import cz.krokviak.agents.tool.Tool;

import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;

public final class Agent<TContext> {
    private final String name;
    private final String instructions;
    private final BiFunction<RunContext<TContext>, Agent<TContext>, String> dynamicInstructions;
    private final String model;
    private final ModelSettings modelSettings;
    private final List<Tool> tools;
    private final List<Handoff<TContext>> handoffs;
    private final List<InputGuardrail<TContext>> inputGuardrails;
    private final List<OutputGuardrail<TContext>> outputGuardrails;
    private final Class<?> outputType;
    private final AgentHooks<TContext> hooks;
    private final ToolUseBehavior toolUseBehavior;
    private final String handoffDescription;

    Agent(String name, String instructions,
          BiFunction<RunContext<TContext>, Agent<TContext>, String> dynamicInstructions,
          String model, ModelSettings modelSettings,
          List<Tool> tools, List<Handoff<TContext>> handoffs,
          List<InputGuardrail<TContext>> inputGuardrails,
          List<OutputGuardrail<TContext>> outputGuardrails,
          Class<?> outputType, AgentHooks<TContext> hooks,
          ToolUseBehavior toolUseBehavior, String handoffDescription) {
        this.name = name;
        this.instructions = instructions;
        this.dynamicInstructions = dynamicInstructions;
        this.model = model;
        this.modelSettings = modelSettings;
        this.tools = Collections.unmodifiableList(tools);
        this.handoffs = Collections.unmodifiableList(handoffs);
        this.inputGuardrails = Collections.unmodifiableList(inputGuardrails);
        this.outputGuardrails = Collections.unmodifiableList(outputGuardrails);
        this.outputType = outputType;
        this.hooks = hooks;
        this.toolUseBehavior = toolUseBehavior;
        this.handoffDescription = handoffDescription;
    }

    public static <T> AgentBuilder<T> builder(String name) {
        return new AgentBuilder<T>().name(name);
    }

    public AgentBuilder<TContext> toBuilder() {
        return new AgentBuilder<TContext>()
            .name(name)
            .instructions(instructions)
            .dynamicInstructions(dynamicInstructions)
            .model(model)
            .modelSettings(modelSettings)
            .tools(tools)
            .handoffs(handoffs)
            .inputGuardrails(inputGuardrails)
            .outputGuardrails(outputGuardrails)
            .outputType(outputType)
            .hooks(hooks)
            .toolUseBehavior(toolUseBehavior)
            .handoffDescription(handoffDescription);
    }

    public Tool asTool(String description) {
        return new AgentTool("transfer_to_" + name, description, name);
    }

    public String resolveInstructions(RunContext<TContext> ctx) {
        if (dynamicInstructions != null) {
            return dynamicInstructions.apply(ctx, this);
        }
        return instructions != null ? instructions : "";
    }

    // Getters
    public String name() { return name; }
    public String instructions() { return instructions; }
    public BiFunction<RunContext<TContext>, Agent<TContext>, String> dynamicInstructions() { return dynamicInstructions; }
    public String model() { return model; }
    public ModelSettings modelSettings() { return modelSettings; }
    public List<Tool> tools() { return tools; }
    public List<Handoff<TContext>> handoffs() { return handoffs; }
    public List<InputGuardrail<TContext>> inputGuardrails() { return inputGuardrails; }
    public List<OutputGuardrail<TContext>> outputGuardrails() { return outputGuardrails; }
    public Class<?> outputType() { return outputType; }
    public AgentHooks<TContext> hooks() { return hooks; }
    public ToolUseBehavior toolUseBehavior() { return toolUseBehavior; }
    public String handoffDescription() { return handoffDescription; }
}
