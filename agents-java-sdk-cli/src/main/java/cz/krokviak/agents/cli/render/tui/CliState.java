package cz.krokviak.agents.cli.render.tui;

import dev.tamboui.toolkit.element.StyledElement;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Centralized mutable state for the TUI. Mutated only on the render thread.
 * Components read from this to build their Element trees.
 */
public final class CliState {

    private static final int MAX_OUTPUT_LINES = 5000;
    static final int COLLAPSED_PREVIEW_LINES = 2;

    // --- Output log ---
    private final List<StyledElement<?>> outputLines = new ArrayList<>();

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

    public void addOutput(StyledElement<?> element) {
        outputLines.add(element);
        if (outputLines.size() > MAX_OUTPUT_LINES) {
            outputLines.subList(0, outputLines.size() - MAX_OUTPUT_LINES).clear();
        }
        lastResultExpanded = false;
    }

    public List<StyledElement<?>> outputLines() { return outputLines; }

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
}
