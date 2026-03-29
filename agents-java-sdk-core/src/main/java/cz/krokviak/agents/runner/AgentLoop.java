package cz.krokviak.agents.runner;

import cz.krokviak.agents.agent.Agent;
import cz.krokviak.agents.context.RunContext;
import cz.krokviak.agents.context.ToolContext;
import cz.krokviak.agents.exception.InputGuardrailTrippedException;
import cz.krokviak.agents.exception.MaxTurnsExceededException;
import cz.krokviak.agents.exception.OutputGuardrailTrippedException;
import cz.krokviak.agents.guardrail.*;
import cz.krokviak.agents.handoff.Handoff;
import cz.krokviak.agents.mcp.MCPConfig;
import cz.krokviak.agents.mcp.MCPServer;
import cz.krokviak.agents.model.*;
import cz.krokviak.agents.tool.*;
import cz.krokviak.agents.tracing.Span;
import cz.krokviak.agents.tracing.Tracing;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AgentLoop {

    private AgentLoop() {}

    @SuppressWarnings("unchecked")
    public static <T> RunResult<T> run(
            Agent<T> agent,
            List<InputItem> input,
            RunContext<T> ctx,
            Model model,
            int maxTurns
    ) {
        return run(agent, input, ctx, model, maxTurns, null);
    }

    @SuppressWarnings("unchecked")
    public static <T> RunResult<T> run(
            Agent<T> agent,
            List<InputItem> input,
            RunContext<T> ctx,
            Model model,
            int maxTurns,
            RunConfig<T> runConfig
    ) {
        Agent<T> currentAgent = agent;
        List<InputItem> messages = new ArrayList<>(input);
        List<RunItem> allItems = new ArrayList<>();
        int turns = 0;

        // Resolve MCP tools from the agent's MCP servers
        List<MCPTool> mcpTools = resolveMCPTools(currentAgent);

        // Run input guardrails in parallel before starting the loop
        runInputGuardrails(currentAgent, ctx, input);

        try (Span agentSpan = Tracing.agentSpan(currentAgent.name())) {

        while (turns < maxTurns) {
            turns++;

            // Build LLM context
            String systemPrompt = currentAgent.resolveInstructions(ctx);
            List<ToolDefinition> toolDefs = buildToolDefinitions(currentAgent, mcpTools);
            LlmContext llmCtx = new LlmContext(systemPrompt, List.copyOf(messages),
                toolDefs, currentAgent.outputType(), currentAgent.modelSettings());

            // Call model
            ModelResponse response;
            String modelName = currentAgent.model() != null ? currentAgent.model() : "unknown";
            try (Span span = Tracing.generationSpan(modelName)) {
                span.setAttribute("agent", currentAgent.name());
                response = model.call(llmCtx, currentAgent.modelSettings());
            }

            ctx.addUsage(response.usage());

            // Process outputs
            boolean hasToolCalls = false;
            boolean hasHandoff = false;
            Agent<T> handoffTarget = null;

            for (ModelResponse.OutputItem output : response.output()) {
                switch (output) {
                    case ModelResponse.OutputItem.Message msg -> {
                        allItems.add(new RunItem.MessageOutput(currentAgent.name(), msg.content()));
                        messages.add(new InputItem.AssistantMessage(msg.content()));

                        // Final output
                        T finalOutput = (T) msg.content();
                        return new RunResult<>(finalOutput, List.copyOf(allItems),
                            currentAgent, List.copyOf(input), ctx.usage(),
                            List.of(), GuardrailResults.empty());
                    }
                    case ModelResponse.OutputItem.ToolCallRequest toolCall -> {
                        hasToolCalls = true;
                        allItems.add(new RunItem.ToolCallItem(currentAgent.name(),
                            toolCall.id(), toolCall.name(), toolCall.arguments()));
                        messages.add(new InputItem.AssistantMessage("",
                            List.of(new InputItem.ToolCall(toolCall.id(), toolCall.name(), toolCall.arguments()))));

                        // Check if this is a handoff
                        Handoff<T> handoff = findHandoff(currentAgent, toolCall.name());
                        if (handoff != null) {
                            hasHandoff = true;
                            String toAgentName = handoff.agent().name();
                            try (Span handoffSpan = Tracing.handoffSpan(currentAgent.name(), toAgentName)) {
                                handoffTarget = handoff.agent();
                                allItems.add(new RunItem.HandoffItem(currentAgent.name(), toAgentName));
                                if (handoff.onHandoff() != null) {
                                    handoff.onHandoff().accept(ctx);
                                }
                                if (handoff.inputFilter() != null) {
                                    messages = new ArrayList<>(handoff.inputFilter().apply(messages));
                                }
                            }
                        } else {
                            // Check tool input guardrails
                            ToolCallData toolCallData = new ToolCallData(toolCall.name(), toolCall.arguments());
                            for (ToolInputGuardrail<T> guard : currentAgent.toolInputGuardrails()) {
                                try (Span guardSpan = Tracing.guardrailSpan(guard.name())) {
                                    GuardrailResult r = guard.execute(ctx, toolCallData);
                                    if (r.tripped()) {
                                        throw new InputGuardrailTrippedException(guard.name(), r.reason());
                                    }
                                }
                            }
                            // Execute tool
                            ToolOutput toolOutput = executeTool(currentAgent, toolCall, ctx, mcpTools);
                            // Check tool output guardrails
                            ToolOutputData toolOutputData = new ToolOutputData(toolCall.name(), toolOutput);
                            for (ToolOutputGuardrail<T> guard : currentAgent.toolOutputGuardrails()) {
                                try (Span guardSpan = Tracing.guardrailSpan(guard.name())) {
                                    GuardrailResult r = guard.execute(ctx, toolOutputData);
                                    if (r.tripped()) {
                                        throw new OutputGuardrailTrippedException(guard.name(), r.reason());
                                    }
                                }
                            }
                            String outputText = toolOutput instanceof ToolOutput.Text t ? t.content() : "[non-text output]";
                            allItems.add(new RunItem.ToolOutputItem(currentAgent.name(),
                                toolCall.id(), toolCall.name(), toolOutput));
                            messages.add(new InputItem.ToolResult(toolCall.id(), toolCall.name(), outputText));
                        }
                    }
                }
            }

            if (hasHandoff && handoffTarget != null) {
                currentAgent = handoffTarget;
                continue;
            }

            if (!hasToolCalls) {
                // No output items at all -- handle gracefully
                return new RunResult<>(null, List.copyOf(allItems),
                    currentAgent, List.copyOf(input), ctx.usage(),
                    List.of(), GuardrailResults.empty());
            }
            // Tool calls processed, loop again for LLM to process results
        }

        } // close agentSpan
        finally {
            closeMCPServers(agent);
        }

        // Check if there's an error handler for "max_turns"
        if (runConfig != null && runConfig.errorHandlers().containsKey("max_turns")) {
            ErrorHandler<T> handler = runConfig.errorHandlers().get("max_turns");
            MaxTurnsExceededException ex = new MaxTurnsExceededException(maxTurns);
            Object handlerResult = handler.handle(ctx, ex);
            T finalOutput = (T) handlerResult;
            return new RunResult<>(finalOutput, List.copyOf(allItems),
                currentAgent, List.copyOf(input), ctx.usage(),
                List.of(), GuardrailResults.empty());
        }

        throw new MaxTurnsExceededException(maxTurns);
    }

    private static <T> List<ToolDefinition> buildToolDefinitions(Agent<T> agent, List<MCPTool> mcpTools) {
        List<ToolDefinition> defs = new ArrayList<>();
        for (Tool tool : agent.tools()) {
            if (!tool.isEnabled()) continue;
            if (tool instanceof FunctionToolImpl ft) {
                defs.add(ft.definition());
            }
        }
        // Add MCP tool definitions
        for (MCPTool mcpTool : mcpTools) {
            defs.add(mcpTool.definition());
        }
        for (Handoff<T> handoff : agent.handoffs()) {
            defs.add(new ToolDefinition(
                handoff.toolName(),
                handoff.description() != null ? handoff.description() : "Transfer to " + handoff.agent().name(),
                Map.of("type", "object", "properties", Map.of(), "required", List.of())
            ));
        }
        return defs;
    }

    private static <T> Handoff<T> findHandoff(Agent<T> agent, String toolName) {
        for (Handoff<T> handoff : agent.handoffs()) {
            if (handoff.toolName().equals(toolName)) {
                return handoff;
            }
        }
        return null;
    }

    private static <T> void runInputGuardrails(Agent<T> agent, RunContext<T> ctx, List<InputItem> input) {
        if (agent.inputGuardrails().isEmpty()) return;
        GuardrailInputData data = new GuardrailInputData(input);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<GuardrailResult>> futures = agent.inputGuardrails().stream()
                .map(g -> CompletableFuture.supplyAsync(
                    () -> {
                        try (Span guardSpan = Tracing.guardrailSpan(g.name())) {
                            return g.execute(ctx, data);
                        }
                    }, executor))
                .toList();
            // Collect all results
            List<GuardrailResult> results = futures.stream()
                .map(CompletableFuture::join)
                .toList();
            // Check each guardrail result in order
            for (int i = 0; i < results.size(); i++) {
                GuardrailResult result = results.get(i);
                if (result.tripped()) {
                    throw new InputGuardrailTrippedException(
                        agent.inputGuardrails().get(i).name(), result.reason());
                }
            }
        }
    }

    private static <T> ToolOutput executeTool(Agent<T> agent, ModelResponse.OutputItem.ToolCallRequest toolCall,
                                              RunContext<T> ctx, List<MCPTool> mcpTools) {
        for (Tool tool : agent.tools()) {
            if (tool.name().equals(toolCall.name()) && tool instanceof FunctionToolImpl ft) {
                try (Span span = Tracing.functionSpan(tool.name())) {
                    ToolContext<T> toolCtx = new ToolContext<>(ctx, toolCall.id());
                    return ft.execute(new ToolArgs(toolCall.arguments()), toolCtx);
                }
            }
        }
        // Check MCP tools
        for (MCPTool mcpTool : mcpTools) {
            if (mcpTool.name().equals(toolCall.name())) {
                try (Span span = Tracing.functionSpan(mcpTool.name())) {
                    return mcpTool.execute(toolCall.arguments());
                } catch (Exception e) {
                    return ToolOutput.text("MCP tool error: " + e.getMessage());
                }
            }
        }
        return ToolOutput.text("Error: unknown tool " + toolCall.name());
    }

    /**
     * Resolve MCP tools from the agent's MCP servers.
     * Connects each server, lists tools, filters by MCPConfig, wraps as MCPTool.
     */
    private static <T> List<MCPTool> resolveMCPTools(Agent<T> agent) {
        if (agent.mcpServers() == null || agent.mcpServers().isEmpty()) {
            return List.of();
        }
        MCPConfig config = agent.mcpConfig() != null ? agent.mcpConfig() : MCPConfig.defaults();
        List<MCPTool> mcpTools = new ArrayList<>();

        for (MCPServer server : agent.mcpServers()) {
            try {
                server.connect();
                List<ToolDefinition> serverTools = server.listTools();
                for (ToolDefinition toolDef : serverTools) {
                    if (config.isToolAllowed(toolDef.name())) {
                        mcpTools.add(new MCPTool(toolDef, server));
                    }
                }
            } catch (Exception e) {
                if ("raise".equals(config.errorHandling())) {
                    throw new RuntimeException("Failed to connect to MCP server: " + e.getMessage(), e);
                }
                // "ignore" — skip this server
            }
        }
        return mcpTools;
    }

    /**
     * Close all MCP servers attached to the agent.
     */
    private static <T> void closeMCPServers(Agent<T> agent) {
        if (agent.mcpServers() == null) return;
        for (MCPServer server : agent.mcpServers()) {
            try {
                server.close();
            } catch (Exception e) {
                // Best effort cleanup
            }
        }
    }
}
