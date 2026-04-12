package cz.krokviak.agents.cli.render;

import cz.krokviak.agents.agent.spawn.AgentStatus;

import java.util.Map;

/**
 * Plain-text renderer for piped/non-TTY output. No ANSI, no interactive prompts.
 * Rich render methods use defaults from Renderer interface.
 */
public final class PlainRenderer implements Renderer {

    @Override public void startSpinner(String message) { System.out.println("[ ] " + message); }
    @Override public void stopSpinner() {}

    @Override
    public void printToolCall(String name, Map<String, Object> args) {
        System.out.println();
        System.out.println("--- " + name + " ---");
        if (args != null) {
            for (var e : args.entrySet()) {
                String v = String.valueOf(e.getValue());
                System.out.println("  " + e.getKey() + ": " + cz.krokviak.agents.util.StringUtils.truncate(v, 200));
            }
        }
    }

    @Override
    public void printToolResult(String name, String output) {
        if (output == null || output.isEmpty()) return;
        String[] lines = output.split("\n", -1);
        for (int i = 0; i < Math.min(lines.length, 50); i++) System.out.println(lines[i]);
        if (lines.length > 50) System.out.println("... (" + (lines.length - 50) + " more lines)");
    }

    @Override
    public void printToolTiming(long startNanos) {
        System.out.println("  completed in " + String.format("%.1fs", (System.nanoTime() - startNanos) / 1e9));
    }

    @Override public void printTextDelta(String delta) { System.out.print(delta); }
    @Override public void printError(String message) { System.out.println("ERROR: " + message); }
    @Override public void printPrompt() { System.out.println(); System.out.print("you> "); System.out.flush(); }
    @Override public void printPromptWithCost(String c) { System.out.println(); System.out.print("you [" + c + "]> "); System.out.flush(); }
    @Override public void printUsage(String f) { System.out.println(f); }
    @Override public void println(String t) { System.out.println(t); }
    @Override public void printPermissionDenied(String t) { System.out.println("WARNING: Permission denied for " + t); }

    @Override
    public void renderAgentStatus(String name, AgentStatus status, String detail) {
        String icon = switch (status) {
            case STARTING, RUNNING -> "~"; case WAITING -> "...";
            case COMPLETED -> "+"; case FAILED, KILLED -> "!";
        };
        System.out.println(icon + " " + name + "  " + status.name().toLowerCase()
            + (detail != null ? "  " + detail : ""));
    }
}
