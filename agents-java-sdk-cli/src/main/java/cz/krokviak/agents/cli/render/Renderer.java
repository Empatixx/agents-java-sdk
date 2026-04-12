package cz.krokviak.agents.cli.render;

import cz.krokviak.agents.agent.spawn.AgentStatus;

import java.util.List;
import java.util.Map;

/**
 * Core renderer interface — output only, no interactive prompts.
 */
public interface Renderer {

    void println(String text);
    void printError(String message);
    void printTextDelta(String delta);
    void printToolCall(String name, Map<String, Object> args);
    void printToolResult(String name, String output);
    void printToolTiming(long startNanos);
    void printPermissionDenied(String toolName);
    void printPrompt();
    void printPromptWithCost(String costInfo);
    void printUsage(String formatted);
    void startSpinner(String message);
    void stopSpinner();

    // --- Rich rendering (default no-ops for PlainRenderer) ---

    default void renderDiff(String diff) { for (var l : diff.split("\n")) println(l); }
    default void renderToolCall(String name, Map<String, Object> args, ToolCallStatus status) {}
    default void renderAgentStatus(String agentName, AgentStatus status, String detail) {}
    default void renderProgress(String label, int current, int total) {}
    default void renderSpinner(String label) { startSpinner(label); }
    default void stopSpinner(String finalMessage) { stopSpinner(); println(finalMessage); }
    default void renderContextBar(int used, int total) {}
    default void renderBox(String title, List<String> content, BoxStyle style) {
        if (title != null) println("── " + title + " ──");
        for (var l : content) println("  " + l);
    }
    default void renderTable(List<String> headers, List<List<String>> rows) {
        println(String.join(" | ", headers));
        for (var r : rows) println(String.join(" | ", r));
    }
    default void renderMarkdown(String markdown) { for (var l : markdown.split("\n")) println(l); }

    /** Tag current thread with agent ID for tool call grouping. */
    default void setCurrentAgent(String agentId) {}
    default void clearCurrentAgent() {}
}
