package cz.krokviak.agents.cli.tool;

import cz.krokviak.agents.api.AgentService;
import cz.krokviak.agents.context.ToolContext;
import cz.krokviak.agents.tool.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * Ask the user one or more questions with selectable options. All prompts
 * go through {@link AgentService#requestQuestion(String, List)} so any
 * frontend (TUI dialog, GraphQL subscription, ...) can answer.
 */
public class AskUserQuestionTool implements ExecutableTool {
    private final AgentService agent;
    private final ToolDefinition toolDefinition;

    public AskUserQuestionTool(AgentService agent) {
        this.agent = agent;

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
                "question", Map.of("type", "string", "description", "(legacy) Single question text"),
                "options", Map.of("type", "array", "items", Map.of("type", "string"),
                    "description", "(legacy) Single question options")
            ), "required", List.of()));
    }

    @Override public String name() { return "ask_user"; }
    @Override public String description() { return toolDefinition.description(); }
    @Override public ToolDefinition definition() { return toolDefinition; }

    private record Question(String header, String text, List<String> options) {}

    @Override
    public ToolOutput execute(ToolArgs args, ToolContext<?> toolCtx) throws Exception {
        List<Question> questions = parseQuestions(args);
        if (questions.isEmpty()) return ToolOutput.text("Error: at least one question required");

        if (questions.stream().anyMatch(q -> q.options().isEmpty())) {
            // Free-text questions: the user's next turn is their answer.
            Question q = questions.getFirst();
            return ToolOutput.text(
                "Question shown to user: \"" + q.text() + "\". " +
                "The user's next message will be their answer. Wait for it.");
        }

        if (questions.size() == 1) {
            Question q = questions.getFirst();
            String header = (q.header() != null ? "〔" + q.header() + "〕 " : "❓ ") + q.text();
            int selected = awaitAnswer(header, q.options());
            String answer = (selected >= 0 && selected < q.options().size()) ? q.options().get(selected) : "(no answer)";
            return ToolOutput.text("User answered: " + answer);
        }

        Map<String, String> answers = new LinkedHashMap<>();
        for (int i = 0; i < questions.size(); i++) {
            Question q = questions.get(i);
            String tag = q.header() != null ? q.header() : "Q" + (i + 1);
            String header = tag + " (" + (i + 1) + "/" + questions.size() + "): " + q.text();
            int selected = awaitAnswer(header, q.options());
            String answer = (selected >= 0 && selected < q.options().size()) ? q.options().get(selected) : "(no answer)";
            answers.put(tag, answer);
        }

        StringBuilder result = new StringBuilder();
        for (var entry : answers.entrySet()) {
            if (!result.isEmpty()) result.append("\n");
            result.append(entry.getKey()).append(": ").append(entry.getValue());
        }
        return ToolOutput.text("User answers:\n" + result);
    }

    private int awaitAnswer(String header, List<String> options) {
        try {
            return agent.requestQuestion(header, options).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return -1;
        } catch (ExecutionException e) {
            return -1;
        }
    }

    private List<Question> parseQuestions(ToolArgs args) {
        List<Question> result = new ArrayList<>();
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
