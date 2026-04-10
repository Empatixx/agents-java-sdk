package cz.krokviak.agents.cli.render.tui;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Centralized mutable state for the TUI. Mutated only on the render thread.
 * Components read from this to build their Element trees.
 */
public final class CliState {

    private static final int MAX_OUTPUT_LINES = 5000;
    static final int COLLAPSED_PREVIEW_LINES = 2;

    // --- Output log (reactive — list rebuilds from this each render) ---
    private final List<OutputLine> outputLines = new ArrayList<>();

    // --- Spinner ---
    private boolean spinnerActive;
    private String spinnerLabel = "";

    // --- Streaming text ---
    private final StringBuilder streamingBuffer = new StringBuilder();

    // --- Status bar ---
    private String model = "";
    private double cost;
    private String permMode = "default";
    private int tokensUsed;
    private int tokensMax;

    // --- Collapsed tool results ---
    public record CollapsedResult(String output, int totalLines) {}
    private final Deque<CollapsedResult> collapsedResults = new ArrayDeque<>();
    private boolean lastResultExpanded;

    // ---- Output ----

    public void addLine(OutputLine line) {
        outputLines.add(line);
        if (outputLines.size() > MAX_OUTPUT_LINES) {
            outputLines.subList(0, outputLines.size() - MAX_OUTPUT_LINES).clear();
        }
    }

    /** Find last line matching predicate and replace it in-place. */
    @SuppressWarnings("unchecked")
    public <T extends OutputLine> boolean updateLast(Class<T> type, Function<T, T> updater) {
        for (int i = outputLines.size() - 1; i >= 0; i--) {
            if (type.isInstance(outputLines.get(i))) {
                outputLines.set(i, updater.apply((T) outputLines.get(i)));
                return true;
            }
        }
        return false;
    }

    public List<OutputLine> outputLines() { return outputLines; }

    // ---- Streaming ----

    public void appendStreaming(String delta) {
        streamingBuffer.append(delta);
    }

    /** Flush completed lines from streaming buffer into output. Returns true if anything flushed. */
    public boolean flushStreamingBuffer(java.util.function.Consumer<String> lineConsumer) {
        String content = streamingBuffer.toString();
        int lastNewline = content.lastIndexOf('\n');
        if (lastNewline < 0) return false;
        String completed = content.substring(0, lastNewline);
        for (String line : completed.split("\n", -1)) {
            lineConsumer.accept(line);
        }
        streamingBuffer.setLength(0);
        streamingBuffer.append(content.substring(lastNewline + 1));
        return true;
    }

    public void flushAll(java.util.function.Consumer<String> lineConsumer) {
        if (streamingBuffer.length() > 0) {
            lineConsumer.accept(streamingBuffer.toString());
            streamingBuffer.setLength(0);
        }
    }

    // ---- Spinner ----

    public void setSpinner(boolean active, String label) {
        this.spinnerActive = active;
        this.spinnerLabel = label != null ? label : "";
    }

    public boolean spinnerActive() { return spinnerActive; }
    public String spinnerLabel() { return spinnerLabel; }

    // ---- Status bar ----

    public void setModel(String model) { this.model = model; }
    public void setCost(double cost) { this.cost = cost; }
    public void setPermMode(String mode) { this.permMode = mode; }
    public void setTokens(int used, int max) { this.tokensUsed = used; this.tokensMax = max; }

    public String model() { return model; }
    public double cost() { return cost; }
    public String permMode() { return permMode; }
    public int tokensUsed() { return tokensUsed; }
    public int tokensMax() { return tokensMax; }

    // ---- Collapsed results ----

    public void pushCollapsed(CollapsedResult result) {
        collapsedResults.addLast(result);
        while (collapsedResults.size() > 50) collapsedResults.pollFirst();
        lastResultExpanded = false;
    }

    public CollapsedResult popCollapsed() { return collapsedResults.pollLast(); }
    public boolean hasCollapsed() { return !collapsedResults.isEmpty(); }
    public boolean isLastResultExpanded() { return lastResultExpanded; }
    public void setLastResultExpanded(boolean expanded) { this.lastResultExpanded = expanded; }

    // ---- Permission prompt ----

    private String permissionHeader;
    private String[] permissionOptions;

    public void setPermissionPrompt(String header, String[] options) {
        this.permissionHeader = header;
        this.permissionOptions = options;
    }

    public void clearPermissionPrompt() {
        this.permissionHeader = null;
        this.permissionOptions = null;
    }

    public boolean hasPermissionPrompt() { return permissionOptions != null; }
    public String permissionHeader() { return permissionHeader; }
    public String[] permissionOptions() { return permissionOptions; }

    // ---- Active agent tool call tracking ----

    private String activeAgentName;
    private String agentDetail = "";
    private final List<String> agentToolCalls = new ArrayList<>();

    public void setActiveAgent(String name) {
        this.activeAgentName = name;
        this.agentDetail = "";
        agentToolCalls.clear();
    }

    public void clearActiveAgent() {
        this.activeAgentName = null;
        this.agentDetail = "";
        agentToolCalls.clear();
    }

    public void setAgentDetail(String detail) { this.agentDetail = detail; }
    public String agentDetail() { return agentDetail; }

    public void pushAgentToolCall(String line) {
        agentToolCalls.add(line);
        if (agentToolCalls.size() > 5) {
            agentToolCalls.removeFirst();
        }
    }

    public String activeAgentName() { return activeAgentName; }
    public List<String> agentToolCalls() { return agentToolCalls; }

    /**
     * When more than maxVisible ToolCall lines exist, remove the oldest ones
     * (+ their CollapseHints) and replace with a single "(N more tool uses, ctrl+o)" hint.
     * Ctrl+O can restore them.
     */
    public void collapseOldToolLines(int maxVisible) {
        // Count visible ToolCall lines
        int count = 0;
        for (OutputLine line : outputLines) {
            if (line instanceof OutputLine.ToolCall) count++;
        }
        if (count < maxVisible) return;

        // Remove oldest ToolCall groups until we have maxVisible-1 (room for new one)
        int removed = 0;
        while (count >= maxVisible) {
            for (int i = 0; i < outputLines.size(); i++) {
                if (outputLines.get(i) instanceof OutputLine.ToolCall) {
                    outputLines.remove(i);
                    removed++;
                    // Remove following CollapseHint too
                    if (i < outputLines.size() && outputLines.get(i) instanceof OutputLine.CollapseHint) {
                        outputLines.remove(i);
                    }
                    count--;
                    break;
                }
            }
        }

        // Update or insert collapsed tools hint
        boolean updated = false;
        for (int i = 0; i < outputLines.size(); i++) {
            if (outputLines.get(i) instanceof OutputLine.Dim d && d.content().contains("tool uses")) {
                outputLines.set(i, new OutputLine.Dim("(" + (removed + extractCount(d.content())) + " more tool uses, ctrl+o to expand)"));
                updated = true;
                break;
            }
        }
        if (!updated && removed > 0) {
            // Find position before first remaining ToolCall
            int pos = 0;
            for (int i = 0; i < outputLines.size(); i++) {
                if (outputLines.get(i) instanceof OutputLine.ToolCall) { pos = i; break; }
            }
            outputLines.add(pos, new OutputLine.Dim("(" + removed + " more tool uses, ctrl+o to expand)"));
        }
    }

    private int extractCount(String s) {
        try {
            return Integer.parseInt(s.replaceAll("[^0-9]", "").substring(0, s.replaceAll("[^0-9]", "").length()));
        } catch (Exception e) { return 0; }
    }

    // ---- Command trie ----

    private final CommandTrie commandTrie = new CommandTrie();

    public CommandTrie commandTrie() { return commandTrie; }

    public List<CommandTrie.Match> suggestCommands(String input) {
        if (input == null || !input.startsWith("/") || input.length() < 2) {
            return List.of();
        }
        String prefix = input.substring(1); // strip "/"
        return commandTrie.search(prefix);
    }
}
