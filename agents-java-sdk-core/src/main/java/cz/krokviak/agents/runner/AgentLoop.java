package cz.krokviak.agents.runner;

import cz.krokviak.agents.agent.Agent;
import cz.krokviak.agents.context.RunContext;
import cz.krokviak.agents.context.ToolContext;
import cz.krokviak.agents.exception.MaxTurnsExceededException;
import cz.krokviak.agents.guardrail.GuardrailResults;
import cz.krokviak.agents.handoff.Handoff;
import cz.krokviak.agents.model.*;
import cz.krokviak.agents.tool.*;
import cz.krokviak.agents.tracing.Span;
import cz.krokviak.agents.tracing.Tracing;

import java.util.*;

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
        Agent<T> currentAgent = agent;
        List<InputItem> messages = new ArrayList<>(input);
        List<RunItem> allItems = new ArrayList<>();
        int turns = 0;

        while (turns < maxTurns) {
            turns++;

            // Build LLM context
            String systemPrompt = currentAgent.resolveInstructions(ctx);
            List<ToolDefinition> toolDefs = buildToolDefinitions(currentAgent);
            LlmContext llmCtx = new LlmContext(systemPrompt, List.copyOf(messages),
                toolDefs, currentAgent.outputType(), currentAgent.modelSettings());

            // Call model
            ModelResponse response;
            try (Span span = Tracing.span("generation")) {
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
                            handoffTarget = handoff.agent();
                            allItems.add(new RunItem.HandoffItem(currentAgent.name(), handoff.agent().name()));
                            if (handoff.onHandoff() != null) {
                                handoff.onHandoff().accept(ctx);
                            }
                            if (handoff.inputFilter() != null) {
                                messages = new ArrayList<>(handoff.inputFilter().apply(messages));
                            }
                        } else {
                            // Execute tool
                            ToolOutput toolOutput = executeTool(currentAgent, toolCall, ctx);
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

        throw new MaxTurnsExceededException(maxTurns);
    }

    private static <T> List<ToolDefinition> buildToolDefinitions(Agent<T> agent) {
        List<ToolDefinition> defs = new ArrayList<>();
        for (Tool tool : agent.tools()) {
            if (!tool.isEnabled()) continue;
            if (tool instanceof FunctionToolImpl ft) {
                defs.add(ft.definition());
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

    private static <T> ToolOutput executeTool(Agent<T> agent, ModelResponse.OutputItem.ToolCallRequest toolCall, RunContext<T> ctx) {
        for (Tool tool : agent.tools()) {
            if (tool.name().equals(toolCall.name()) && tool instanceof FunctionToolImpl ft) {
                try (Span span = Tracing.span("function:" + tool.name())) {
                    ToolContext<T> toolCtx = new ToolContext<>(ctx, toolCall.id());
                    return ft.execute(new ToolArgs(toolCall.arguments()), toolCtx);
                }
            }
        }
        return ToolOutput.text("Error: unknown tool " + toolCall.name());
    }
}
