package cz.krokviak.agents.cli.tool;

import cz.krokviak.agents.context.ToolContext;
import cz.krokviak.agents.model.*;
import cz.krokviak.agents.runner.InputItem;
import cz.krokviak.agents.tool.*;

import java.util.List;
import java.util.Map;

public class BriefTool implements ExecutableTool {
    private final Model model;
    private final ToolDefinition toolDefinition;

    public BriefTool(Model model) {
        this.model = model;
        this.toolDefinition = new ToolDefinition("brief",
            "Ask a quick side-question that doesn't add to conversation history and has no tool access. " +
            "Use for quick factual lookups or clarifications.",
            Map.of("type", "object", "properties", Map.of(
                "question", Map.of("type", "string", "description", "The question to ask")
            ), "required", List.of("question")));
    }

    @Override public String name() { return "brief"; }
    @Override public String description() { return toolDefinition.description(); }
    @Override public ToolDefinition definition() { return toolDefinition; }

    @Override
    public ToolOutput execute(ToolArgs args, ToolContext<?> ctx) {
        String question = args.get("question", String.class);
        if (question == null || question.isBlank()) {
            return ToolOutput.text("Error: question required");
        }
        try {
            LlmContext llmContext = new LlmContext(
                question,
                List.of(),
                List.of(),
                null,
                ModelSettings.builder().maxTokens(1024).build()
            );
            ModelResponse response = model.call(llmContext, ModelSettings.builder().maxTokens(1024).build());
            for (var output : response.output()) {
                if (output instanceof ModelResponse.OutputItem.Message msg) {
                    return ToolOutput.text(msg.content());
                }
            }
            return ToolOutput.text("(no response)");
        } catch (Exception e) {
            return ToolOutput.text("Error calling model: " + e.getMessage());
        }
    }
}
