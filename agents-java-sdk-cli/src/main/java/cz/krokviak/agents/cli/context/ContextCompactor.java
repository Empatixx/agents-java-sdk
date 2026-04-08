package cz.krokviak.agents.cli.context;

import cz.krokviak.agents.model.*;
import cz.krokviak.agents.runner.InputItem;
import cz.krokviak.agents.tool.ToolDefinition;

import java.util.ArrayList;
import java.util.List;

public class ContextCompactor {

    private final Model model;
    private final int maxTokens;
    private final int targetTokens;

    public ContextCompactor(Model model, int maxTokens, int targetTokens) {
        this.model = model;
        this.maxTokens = maxTokens;
        this.targetTokens = targetTokens;
    }

    public ContextCompactor(Model model) {
        this(model, 80_000, 40_000);
    }

    public List<InputItem> compactIfNeeded(List<InputItem> history, String systemPrompt) {
        int totalEstimate = TokenEstimator.estimate(systemPrompt) + TokenEstimator.estimate(history);
        if (totalEstimate <= maxTokens) {
            return history;
        }
        return doCompact(history, totalEstimate);
    }

    public List<InputItem> forceCompact(List<InputItem> history) {
        if (history.size() <= 2) return history;
        return doCompact(history, Integer.MAX_VALUE);
    }

    private List<InputItem> doCompact(List<InputItem> history, int totalEstimate) {
        // Find how many old messages to summarize (target ~50% reduction)
        int tokensToRemove = totalEstimate - targetTokens;
        int tokensAccumulated = 0;
        int splitIndex = 0;

        for (int i = 0; i < history.size() - 2; i++) { // keep at least last 2
            tokensAccumulated += TokenEstimator.estimate(List.of(history.get(i)));
            splitIndex = i + 1;
            if (tokensAccumulated >= tokensToRemove) break;
        }

        if (splitIndex <= 0) return history;

        // Summarize old messages
        List<InputItem> oldMessages = history.subList(0, splitIndex);
        String conversationText = serializeMessages(oldMessages);

        String summaryPrompt = "Summarize the following conversation concisely. " +
            "Preserve: key decisions made, file paths mentioned, current task state, " +
            "any important context. Be brief but complete.\n\n" + conversationText;

        try {
            LlmContext ctx = new LlmContext(
                "You are a conversation summarizer. Output only the summary.",
                List.of(new InputItem.UserMessage(summaryPrompt)),
                List.of(), null, ModelSettings.builder().maxTokens(2048).build()
            );
            ModelResponse response = model.call(ctx, ModelSettings.builder().maxTokens(2048).build());

            String summary = "";
            for (var output : response.output()) {
                if (output instanceof ModelResponse.OutputItem.Message msg) {
                    summary = msg.content();
                    break;
                }
            }

            List<InputItem> compacted = new ArrayList<>();
            compacted.add(new InputItem.SystemMessage("[Conversation Summary]\n" + summary));
            compacted.addAll(history.subList(splitIndex, history.size()));

            System.out.println("\033[2m[Compacted: " + splitIndex + " messages summarized, " +
                tokensAccumulated + " tokens freed]\033[0m");

            return compacted;
        } catch (Exception e) {
            System.out.println("\033[31m[Compaction failed: " + e.getMessage() + "]\033[0m");
            return history;
        }
    }

    private String serializeMessages(List<InputItem> messages) {
        StringBuilder sb = new StringBuilder();
        for (InputItem item : messages) {
            switch (item) {
                case InputItem.UserMessage m -> sb.append("User: ").append(m.content()).append("\n");
                case InputItem.AssistantMessage m -> {
                    sb.append("Assistant: ").append(m.content() != null ? m.content() : "").append("\n");
                    for (var tc : m.toolCalls()) {
                        sb.append("  [Tool call: ").append(tc.name()).append("(").append(tc.arguments()).append(")]\n");
                    }
                }
                case InputItem.ToolResult m -> sb.append("  [Tool result ").append(m.toolName()).append(": ")
                    .append(truncate(m.output(), 200)).append("]\n");
                case InputItem.SystemMessage m -> sb.append("System: ").append(m.content()).append("\n");
            }
        }
        return sb.toString();
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        if (s.length() <= max) return s;
        return s.substring(0, max) + "...";
    }
}
