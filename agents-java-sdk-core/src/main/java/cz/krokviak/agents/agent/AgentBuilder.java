package cz.krokviak.agents.agent;

import cz.krokviak.agents.context.RunContext;
import cz.krokviak.agents.guardrail.InputGuardrail;
import cz.krokviak.agents.guardrail.OutputGuardrail;
import cz.krokviak.agents.guardrail.ToolInputGuardrail;
import cz.krokviak.agents.guardrail.ToolOutputGuardrail;
import cz.krokviak.agents.handoff.Handoff;
import cz.krokviak.agents.hook.AgentHooks;
import cz.krokviak.agents.mcp.MCPConfig;
import cz.krokviak.agents.mcp.MCPServer;
import cz.krokviak.agents.model.ModelSettings;
import cz.krokviak.agents.tool.Tool;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

public final class AgentBuilder<TContext> {
    private String name;
    private String instructions;
    private BiFunction<RunContext<TContext>, Agent<TContext>, String> dynamicInstructions;
    private String model;
    private ModelSettings modelSettings;
    private List<Tool> tools = new ArrayList<>();
    private List<Handoff<TContext>> handoffs = new ArrayList<>();
    private List<InputGuardrail<TContext>> inputGuardrails = new ArrayList<>();
    private List<OutputGuardrail<TContext>> outputGuardrails = new ArrayList<>();
    private List<ToolInputGuardrail<TContext>> toolInputGuardrails = new ArrayList<>();
    private List<ToolOutputGuardrail<TContext>> toolOutputGuardrails = new ArrayList<>();
    private Class<?> outputType;
    private AgentHooks<TContext> hooks;
    private ToolUseBehavior toolUseBehavior = ToolUseBehavior.RUN_LLM_AGAIN;
    private String handoffDescription;
    private List<MCPServer> mcpServers = new ArrayList<>();
    private MCPConfig mcpConfig;

    AgentBuilder() {}

    public AgentBuilder<TContext> name(String name) {
        this.name = name;
        return this;
    }

    public AgentBuilder<TContext> instructions(String instructions) {
        this.instructions = instructions;
        return this;
    }

    public AgentBuilder<TContext> dynamicInstructions(
            BiFunction<RunContext<TContext>, Agent<TContext>, String> dynamicInstructions) {
        this.dynamicInstructions = dynamicInstructions;
        return this;
    }

    public AgentBuilder<TContext> model(String model) {
        this.model = model;
        return this;
    }

    public AgentBuilder<TContext> modelSettings(ModelSettings modelSettings) {
        this.modelSettings = modelSettings;
        return this;
    }

    public AgentBuilder<TContext> tools(List<Tool> tools) {
        this.tools = new ArrayList<>(tools);
        return this;
    }

    public AgentBuilder<TContext> addTool(Tool tool) {
        this.tools.add(tool);
        return this;
    }

    public AgentBuilder<TContext> handoffs(List<Handoff<TContext>> handoffs) {
        this.handoffs = new ArrayList<>(handoffs);
        return this;
    }

    public AgentBuilder<TContext> addHandoff(Handoff<TContext> handoff) {
        this.handoffs.add(handoff);
        return this;
    }

    public AgentBuilder<TContext> inputGuardrails(List<InputGuardrail<TContext>> guardrails) {
        this.inputGuardrails = new ArrayList<>(guardrails);
        return this;
    }

    public AgentBuilder<TContext> outputGuardrails(List<OutputGuardrail<TContext>> guardrails) {
        this.outputGuardrails = new ArrayList<>(guardrails);
        return this;
    }

    public AgentBuilder<TContext> toolInputGuardrails(List<ToolInputGuardrail<TContext>> guardrails) {
        this.toolInputGuardrails = new ArrayList<>(guardrails);
        return this;
    }

    public AgentBuilder<TContext> toolOutputGuardrails(List<ToolOutputGuardrail<TContext>> guardrails) {
        this.toolOutputGuardrails = new ArrayList<>(guardrails);
        return this;
    }

    public AgentBuilder<TContext> outputType(Class<?> outputType) {
        this.outputType = outputType;
        return this;
    }

    public AgentBuilder<TContext> hooks(AgentHooks<TContext> hooks) {
        this.hooks = hooks;
        return this;
    }

    public AgentBuilder<TContext> toolUseBehavior(ToolUseBehavior behavior) {
        this.toolUseBehavior = behavior;
        return this;
    }

    public AgentBuilder<TContext> handoffDescription(String description) {
        this.handoffDescription = description;
        return this;
    }

    public AgentBuilder<TContext> mcpServers(List<MCPServer> mcpServers) {
        this.mcpServers = new ArrayList<>(mcpServers);
        return this;
    }

    public AgentBuilder<TContext> addMcpServer(MCPServer server) {
        this.mcpServers.add(server);
        return this;
    }

    public AgentBuilder<TContext> mcpConfig(MCPConfig mcpConfig) {
        this.mcpConfig = mcpConfig;
        return this;
    }

    public Agent<TContext> build() {
        if (name == null || name.isBlank()) {
            throw new IllegalStateException("Agent name is required");
        }
        return new Agent<>(name, instructions, dynamicInstructions, model, modelSettings,
            tools, handoffs, inputGuardrails, outputGuardrails, toolInputGuardrails, toolOutputGuardrails,
            outputType, hooks, toolUseBehavior, handoffDescription,
            mcpServers, mcpConfig);
    }
}
