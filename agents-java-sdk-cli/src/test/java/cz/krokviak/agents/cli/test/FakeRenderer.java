package cz.krokviak.agents.cli.test;

import cz.krokviak.agents.agent.spawn.AgentStatus;

import cz.krokviak.agents.cli.render.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Captures all output for assertions in tests. No-ops for rich rendering.
 */
public class FakeRenderer implements Renderer {

    private final List<String> lines = new ArrayList<>();
    private final List<String> errors = new ArrayList<>();

    @Override public void println(String text) { lines.add(text); }
    @Override public void printError(String msg) { errors.add(msg); }
    @Override public void printTextDelta(String delta) { lines.add("[delta] " + delta); }
    @Override public void printToolCall(String name, Map<String, Object> args) { lines.add("[tool] " + name); }
    @Override public void printToolResult(String name, String output) { lines.add("[result] " + name); }
    @Override public void printToolTiming(long startNanos) {}
    @Override public void printPermissionDenied(String toolName) { lines.add("[denied] " + toolName); }
    @Override public void printPrompt() {}
    @Override public void printPromptWithCost(String cost) {}
    @Override public void printUsage(String formatted) {}
    @Override public void startSpinner(String message) {}
    @Override public void stopSpinner() {}
    @Override public void renderDiff(String diff) {}
    @Override public void renderToolCall(String name, Map<String, Object> args, ToolCallStatus status) {}
    @Override public void renderAgentStatus(String agentName, AgentStatus status, String detail) {}
    @Override public void renderProgress(String label, int current, int total) {}
    @Override public void renderSpinner(String label) {}
    @Override public void stopSpinner(String finalMessage) {}
    @Override public void renderContextBar(int used, int total) {}
    @Override public void renderBox(String title, List<String> content, BoxStyle style) {}
    @Override public void renderTable(List<String> headers, List<List<String>> rows) {}
    @Override public void renderMarkdown(String markdown) {}

    public List<String> lines() { return lines; }
    public List<String> errors() { return errors; }
}
