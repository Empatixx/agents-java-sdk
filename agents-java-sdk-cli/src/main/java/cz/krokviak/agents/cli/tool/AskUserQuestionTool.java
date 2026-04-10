package cz.krokviak.agents.cli.tool;

import cz.krokviak.agents.cli.CliContext;
import cz.krokviak.agents.cli.render.tui.TuiRenderer;
import cz.krokviak.agents.context.ToolContext;
import cz.krokviak.agents.tool.*;

import java.util.List;
import java.util.Map;

/**
 * Ask the user a question with optional selectable options.
 * With options: shows arrow-key selector (like permission prompt).
 * Without options: shows question, next user message is the answer.
 * Read-only — allowed in plan mode.
 */
public class AskUserQuestionTool implements ExecutableTool {
    private final CliContext ctx;
    private final ToolDefinition toolDefinition;

    public AskUserQuestionTool(CliContext ctx) {
        this.ctx = ctx;
        this.toolDefinition = new ToolDefinition("ask_user",
            "Ask the user a question. With options: shows selectable choices. " +
                "Without options: shows question, user types answer. " +
                "Use when you need clarification, a decision, or user input.",
            Map.of("type", "object", "properties", Map.of(
                "question", Map.of("type", "string", "description", "The question to ask"),
                "options", Map.of("type", "array", "items", Map.of("type", "string"),
                    "description", "2-4 selectable choices (optional)")
            ), "required", List.of("question")));
    }

    @Override public String name() { return "ask_user"; }
    @Override public String description() { return toolDefinition.description(); }
    @Override public ToolDefinition definition() { return toolDefinition; }

    @Override
    @SuppressWarnings("unchecked")
    public ToolOutput execute(ToolArgs args, ToolContext<?> toolCtx) throws Exception {
        String question = args.get("question", String.class);
        if (question == null || question.isBlank()) return ToolOutput.text("Error: question required");

        List<String> options = null;
        Object raw = args.raw().get("options");
        if (raw instanceof List<?> list && !list.isEmpty()) {
            options = list.stream().map(Object::toString).toList();
        }

        TuiRenderer renderer = ctx.tuiRenderer();

        if (options != null && !options.isEmpty() && renderer != null) {
            // Multiple choice — permission selector UI
            String[] opts = options.toArray(new String[0]);
            ctx.output().println("");
            int selected = renderer.promptPermission("❓ " + question, opts);
            String answer = (selected >= 0 && selected < opts.length) ? opts[selected] : "(no answer)";
            ctx.output().println("  → " + answer);
            return ToolOutput.text("User answered: " + answer);
        }

        // Free-text — show question, next user message is the answer
        ctx.output().println("");
        ctx.output().println("❓ " + question);

        if (renderer != null) {
            // Show single option to prompt user to type answer
            String[] hint = {"(Type your answer in the input below)"};
            renderer.promptPermission("❓ " + question, hint);
        }

        return ToolOutput.text(
            "Question shown to user: \"" + question + "\". " +
            "The user's next message will be their answer. Wait for it.");
    }
}
