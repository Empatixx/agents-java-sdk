package cz.krokviak.agents.cli.tool;

import cz.krokviak.agents.cli.CliContext;
import cz.krokviak.agents.cli.render.PromptRenderer;
import cz.krokviak.agents.context.ToolContext;
import cz.krokviak.agents.tool.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ask the user one or more questions with selectable options.
 * Supports multiple questions with category headers, navigable via left/right arrows.
 * Each question has its own options navigable via up/down arrows.
 */
public class AskUserQuestionTool implements ExecutableTool {
    private final CliContext ctx;
    private final ToolDefinition toolDefinition;

    @SuppressWarnings("unchecked")
    public AskUserQuestionTool(CliContext ctx) {
        this.ctx = ctx;

        var questionSchema = Map.of(
            "type", "object",
            "properties", (Object) Map.of(
                "question", Map.of("type", "string", "description", "The question to ask"),
                "header", Map.of("type", "string", "description",
                    "Short category tag (max 12 chars), e.g. 'Security', 'Approach', 'Performance'"),
                "options", Map.of("type", "array", "items", Map.of("type", "string"),
                    "description", "2-4 selectable choices")
            ),
            "required", List.of("question", "options")
        );

        this.toolDefinition = new ToolDefinition("ask_user",
            "Ask the user questions with selectable choices. " +
                "Send multiple questions to let user navigate between them with arrow keys. " +
                "Each question has a short header tag and 2-4 options.",
            Map.of("type", "object", "properties", Map.of(
                "questions", Map.of("type", "array", "items", questionSchema,
                    "description", "1-4 questions to ask the user"),
                // Legacy single-question fields for backward compatibility
                "question", Map.of("type", "string", "description", "(legacy) Single question text"),
                "options", Map.of("type", "array", "items", Map.of("type", "string"),
                    "description", "(legacy) Single question options")
            ), "required", List.of()));
    }

    @Override public String name() { return "ask_user"; }
    @Override public String description() { return toolDefinition.description(); }
    @Override public ToolDefinition definition() { return toolDefinition; }

    /** Parsed question from the tool arguments. */
    private record Question(String header, String text, List<String> options) {}

    @Override
    @SuppressWarnings("unchecked")
    public ToolOutput execute(ToolArgs args, ToolContext<?> toolCtx) throws Exception {
        List<Question> questions = parseQuestions(args);
        if (questions.isEmpty()) return ToolOutput.text("Error: at least one question required");

        PromptRenderer renderer = ctx.promptRenderer();

        if (renderer != null && questions.stream().allMatch(q -> !q.options().isEmpty())) {
            // All questions have options — use multi-question selector UI
            return executeWithSelector(renderer, questions);
        }

        // Fallback: single question, free-text or no TUI
        Question q = questions.getFirst();
        if (q.options().isEmpty()) {
            ctx.output().println("");
            String label = q.header() != null ? "[" + q.header() + "] " : "";
            ctx.output().println("❓ " + label + q.text());
            return ToolOutput.text(
                "Question shown to user: \"" + q.text() + "\". " +
                "The user's next message will be their answer. Wait for it.");
        }

        // Single question with options, no TUI
        ctx.output().println("");
        String label = q.header() != null ? "[" + q.header() + "] " : "";
        ctx.output().println("❓ " + label + q.text());
        for (int i = 0; i < q.options().size(); i++) {
            ctx.output().println("  " + (i + 1) + ". " + q.options().get(i));
        }
        return ToolOutput.text(
            "Question shown to user: \"" + q.text() + "\". " +
            "The user's next message will be their answer. Wait for it.");
    }

    private ToolOutput executeWithSelector(PromptRenderer renderer, List<Question> questions) {
        if (questions.size() == 1) {
            // Single question — simple selector
            Question q = questions.getFirst();
            String header = (q.header() != null ? "〔" + q.header() + "〕 " : "❓ ") + q.text();
            String[] opts = q.options().toArray(new String[0]);
            ctx.output().println("");
            int selected = renderer.promptSelection(header, opts);
            String answer = (selected >= 0 && selected < opts.length) ? opts[selected] : "(no answer)";
            ctx.output().println("  → " + answer);
            return ToolOutput.text("User answered: " + answer);
        }

        // Multiple questions — sequential prompts
        ctx.output().println("");
        Map<String, String> answers = new LinkedHashMap<>();
        for (int i = 0; i < questions.size(); i++) {
            Question q = questions.get(i);
            String tag = q.header() != null ? q.header() : "Q" + (i + 1);
            String header = tag + " (" + (i + 1) + "/" + questions.size() + "): " + q.text();
            String[] opts = q.options().toArray(new String[0]);
            int selected = renderer.promptSelection(header, opts);
            String answer = (selected >= 0 && selected < opts.length) ? opts[selected] : "(no answer)";
            answers.put(tag, answer);
        }

        // Print summary of answers
        StringBuilder result = new StringBuilder();
        for (var entry : answers.entrySet()) {
            ctx.output().println("  " + entry.getKey() + " → " + entry.getValue());
            if (!result.isEmpty()) result.append("\n");
            result.append(entry.getKey()).append(": ").append(entry.getValue());
        }

        return ToolOutput.text("User answers:\n" + result);
    }

    @SuppressWarnings("unchecked")
    private List<Question> parseQuestions(ToolArgs args) {
        List<Question> result = new ArrayList<>();

        // Try new multi-question format first
        Object questionsRaw = args.raw().get("questions");
        if (questionsRaw instanceof List<?> list && !list.isEmpty()) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    String text = m.get("question") instanceof String s ? s : null;
                    String header = m.get("header") instanceof String s ? s : null;
                    List<String> options = new ArrayList<>();
                    if (m.get("options") instanceof List<?> optList) {
                        for (Object o : optList) options.add(o.toString());
                    }
                    if (text != null && !text.isBlank()) {
                        result.add(new Question(header, text, options));
                    }
                }
            }
            if (!result.isEmpty()) return result;
        }

        // Legacy single-question format
        String question = args.raw().get("question") instanceof String s ? s : null;
        if (question == null || question.isBlank()) return result;

        List<String> options = new ArrayList<>();
        Object raw = args.raw().get("options");
        if (raw instanceof List<?> list2) {
            for (Object o : list2) options.add(o.toString());
        }

        result.add(new Question(null, question, options));
        return result;
    }
}
