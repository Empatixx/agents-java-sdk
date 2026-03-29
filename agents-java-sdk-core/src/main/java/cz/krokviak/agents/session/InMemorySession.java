package cz.krokviak.agents.session;

import cz.krokviak.agents.runner.InputItem;
import cz.krokviak.agents.runner.RunItem;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class InMemorySession implements Session {
    private final ConcurrentHashMap<String, List<RunItem>> store = new ConcurrentHashMap<>();

    @Override
    public List<InputItem> getHistory(String sessionId) {
        List<RunItem> items = store.getOrDefault(sessionId, List.of());
        return items.stream()
            .map(InMemorySession::toInputItem)
            .toList();
    }

    @Override
    public synchronized void save(String sessionId, List<RunItem> newItems) {
        store.computeIfAbsent(sessionId, k -> new ArrayList<>()).addAll(newItems);
    }

    private static InputItem toInputItem(RunItem item) {
        return switch (item) {
            case RunItem.MessageOutput msg ->
                new InputItem.AssistantMessage(msg.content());
            case RunItem.ToolCallItem call ->
                new InputItem.AssistantMessage("", List.of(
                    new InputItem.ToolCall(call.toolCallId(), call.toolName(), call.arguments())
                ));
            case RunItem.ToolOutputItem out ->
                new InputItem.ToolResult(out.toolCallId(), out.toolName(),
                    out.output() instanceof cz.krokviak.agents.tool.ToolOutput.Text t ? t.content() : "[non-text]");
            case RunItem.HandoffItem h ->
                new InputItem.AssistantMessage("[Handoff: " + h.fromAgent() + " -> " + h.toAgent() + "]");
        };
    }
}
