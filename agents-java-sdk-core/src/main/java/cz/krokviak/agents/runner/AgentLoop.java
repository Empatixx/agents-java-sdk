package cz.krokviak.agents.runner;

import cz.krokviak.agents.def.Agent;
import cz.krokviak.agents.context.RunContext;
import cz.krokviak.agents.context.ToolContext;
import cz.krokviak.agents.exception.InputGuardrailTrippedException;
import cz.krokviak.agents.exception.MaxTurnsExceededException;
import cz.krokviak.agents.exception.OutputGuardrailTrippedException;
import cz.krokviak.agents.guardrail.*;
import cz.krokviak.agents.handoff.Handoff;
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

        // Resolve tools from ToolProviders
        List<Tool> providerTools = new ArrayList<>();
        for (ToolProvider provider : agent.toolProviders()) {
            try {
                providerTools.addAll(provider.provideTools());
            } catch (Exception e) {
                // skip failed providers
            }
        }

        // Run input guardrails in parallel before starting the loop
        runInputGuardrails(currentAgent, ctx, input);

        try (Span agentSpan = Tracing.agentSpan(currentAgent.name())) {

        while (turns < maxTurns) {
            turns++;

            // Build LLM context
            String systemPrompt = currentAgent.resolveInstructions(ctx);
            List<ToolDefinition> toolDefs = buildToolDefinitions(currentAgent, providerTools);
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
                            // Execute tool (check agent tools + provider tools)
                            ToolOutput toolOutput = executeTool(currentAgent, toolCall, ctx, providerTools);
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
                return new RunResult<>(null, List.copyOf(allItems),
                    currentAgent, List.copyOf(input), ctx.usage(),
                    List.of(), GuardrailResults.empty());
            }
        }

        } // close agentSpan
        finally {
            // Close all ToolProviders
            for (ToolProvider provider : agent.toolProviders()) {
                try { provider.close(); } catch (Exception ignored) {}
            }
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

    private static <T> List<ToolDefinition> buildToolDefinitions(Agent<T> agent, List<Tool> providerTools) {
        List<ToolDefinition> defs = new ArrayList<>();
        for (Tool tool : agent.tools()) {
            if (!tool.isEnabled()) continue;
            if (tool instanceof ExecutableTool et) {
                defs.add(et.definition());
            }
        }
        // Add provider tool definitions
        for (Tool tool : providerTools) {
            if (!tool.isEnabled()) continue;
            if (tool instanceof ExecutableTool et) {
                defs.add(et.definition());
            }
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
            List<GuardrailResult> results = futures.stream()
                .map(CompletableFuture::join)
                .toList();
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
                                              RunContext<T> ctx, List<Tool> providerTools) {
        // Check agent's own tools
        for (Tool tool : agent.tools()) {
            if (tool.name().equals(toolCall.name()) && tool instanceof ExecutableTool et) {
                try (Span span = Tracing.functionSpan(tool.name())) {
                    ToolContext<T> toolCtx = new ToolContext<>(ctx, toolCall.id());
                    return et.execute(new ToolArgs(toolCall.arguments()), toolCtx);
                } catch (Exception e) {
                    return ToolOutput.text("Tool error: " + e.getMessage());
                }
            }
        }
        // Check provider tools
        for (Tool tool : providerTools) {
            if (tool.name().equals(toolCall.name()) && tool instanceof ExecutableTool et) {
                try (Span span = Tracing.functionSpan(tool.name())) {
                    ToolContext<T> toolCtx = new ToolContext<>(ctx, toolCall.id());
                    return et.execute(new ToolArgs(toolCall.arguments()), toolCtx);
                } catch (Exception e) {
                    return ToolOutput.text("Tool error: " + e.getMessage());
                }
            }
        }
        return ToolOutput.text("Error: unknown tool " + toolCall.name());
    }
}
