package cz.krokviak.agents.cli.tool;

import cz.krokviak.agents.cli.CliContext;
import cz.krokviak.agents.context.ToolContext;
import cz.krokviak.agents.tool.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;

/**
 * Allows the model to ask the user a question and get a response.
 * The model uses this when it needs clarification or a decision.
 */
public class AskUserQuestionTool implements ExecutableTool {
    private final CliContext ctx;
    private final ToolDefinition toolDefinition;

    public AskUserQuestionTool(CliContext ctx) {
        this.ctx = ctx;
        this.toolDefinition = new ToolDefinition("ask_user",
            "Ask the user a question and wait for their response. Use this when you need " +
                "clarification, a decision, or user input before proceeding.",
            Map.of("type", "object", "properties", Map.of(
                "question", Map.of("type", "string", "description", "The question to ask the user"),
                "options", Map.of("type", "array", "items", Map.of("type", "string"),
                    "description", "Optional list of choices to present")
            ), "required", List.of("question")));
    }

    @Override public String name() { return "ask_user"; }
    @Override public String description() { return toolDefinition.description(); }
    @Override public ToolDefinition definition() { return toolDefinition; }

    @Override
    @SuppressWarnings("unchecked")
    public ToolOutput execute(ToolArgs args, ToolContext<?> toolCtx) throws Exception {
        String question = args.get("question", String.class);
        if (question == null || question.isBlank()) return ToolOutput.text("Error: question is required");

        ctx.output().println("");
        ctx.output().println("\033[1;33m? " + question + "\033[0m");

        List<String> options = args.get("options", List.class);
        if (options != null && !options.isEmpty()) {
            for (int i = 0; i < options.size(); i++) {
                ctx.output().println("  " + (i + 1) + ". " + options.get(i));
            }
            ctx.output().println("");
        }

        System.out.print("\033[1myour answer> \033[0m");
        System.out.flush();

        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        String answer = reader.readLine();
        if (answer == null) answer = "(no response)";

        // If they entered a number and we had options, resolve it
        if (options != null && !options.isEmpty()) {
            try {
                int idx = Integer.parseInt(answer.trim()) - 1;
                if (idx >= 0 && idx < options.size()) {
                    answer = options.get(idx);
                }
            } catch (NumberFormatException ignored) {}
        }

        return ToolOutput.text("User answered: " + answer.trim());
    }
}
