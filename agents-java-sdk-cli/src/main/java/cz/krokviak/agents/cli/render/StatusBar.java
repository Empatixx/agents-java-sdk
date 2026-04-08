package cz.krokviak.agents.cli.render;

/**
 * Renders a persistent status bar into a {@link ScreenBuffer} row at the bottom of the terminal.
 *
 * <p>Format:
 * <pre>
 *  model: claude-sonnet │ context: [████░░] 65% │ cost: $0.42 │ mode: default
 * </pre>
 *
 * <p>The context bar colour follows a gradient:
 * <ul>
 *   <li>Green  — usage &lt; 70 %</li>
 *   <li>Yellow — 70 %–90 %</li>
 *   <li>Red    — &gt; 90 %</li>
 * </ul>
 */
public final class StatusBar {

    // ANSI colour constants
    private static final String RESET   = "\033[0m";
    private static final String BOLD    = "\033[1m";
    private static final String DIM     = "\033[2m";
    private static final String GREEN   = "\033[32m";
    private static final String YELLOW  = "\033[33m";
    private static final String RED     = "\033[31m";
    private static final String CYAN    = "\033[36m";
    private static final String BG_BAR  = "\033[48;5;236m"; // dark grey background

    private static final int BAR_WIDTH = 6; // number of fill blocks in context bar

    // Mutable state — all updates are protected by the TuiRenderer lock
    private String model        = "";
    private int    tokensUsed   = 0;
    private int    tokensMax    = 0;
    private double cost         = 0.0;
    private String permMode     = "default";

    // ------------------------------------------------------------------
    //  Setters
    // ------------------------------------------------------------------

    public void setModel(String model)           { this.model      = model == null ? "" : model; }
    public void setTokensUsed(int tokensUsed)    { this.tokensUsed = tokensUsed; }
    public void setTokensMax(int tokensMax)      { this.tokensMax  = tokensMax; }
    public void setCost(double cost)             { this.cost       = cost; }
    public void setPermMode(String permMode)     { this.permMode   = permMode == null ? "default" : permMode; }

    // ------------------------------------------------------------------
    //  Render
    // ------------------------------------------------------------------

    /**
     * Write the status bar into {@code buffer} at the given row using the full {@code width}.
     *
     * @param buffer target screen buffer
     * @param row    0-based row to render into (usually the last row)
     * @param width  number of columns available
     */
    public void render(ScreenBuffer buffer, int row, int width) {
        String text = buildText(width);
        // Pad to full width
        if (text.length() < width) {
            text = text + " ".repeat(width - text.length());
        } else if (text.length() > width) {
            text = text.substring(0, width);
        }
        buffer.writeLine(row, text, BG_BAR + DIM);
    }

    // ------------------------------------------------------------------
    //  Private helpers
    // ------------------------------------------------------------------

    private String buildText(int width) {
        String contextSegment = buildContextSegment();
        String costStr        = String.format("$%.2f", cost);
        return " " + CYAN + "model: " + RESET + BG_BAR + DIM + shortModel()
                + " " + DIM + "│" + RESET + BG_BAR + DIM
                + " context: " + contextSegment
                + " " + DIM + "│" + RESET + BG_BAR + DIM
                + " cost: " + RESET + BG_BAR + costStr
                + " " + DIM + "│" + RESET + BG_BAR + DIM
                + " mode: " + permMode + " ";
    }

    private String buildContextSegment() {
        if (tokensMax <= 0) {
            return DIM + "[------]" + RESET + BG_BAR + DIM + " --%" + RESET + BG_BAR;
        }
        double pct    = (double) tokensUsed / tokensMax;
        int filled    = (int) Math.round(pct * BAR_WIDTH);
        filled        = Math.max(0, Math.min(BAR_WIDTH, filled));
        int empty     = BAR_WIDTH - filled;
        int percent   = (int) Math.round(pct * 100);
        String colour = pct >= 0.9 ? RED : pct >= 0.7 ? YELLOW : GREEN;
        String bar    = colour + "█".repeat(filled) + DIM + "░".repeat(empty) + RESET + BG_BAR + DIM;
        return "[" + bar + "] " + percent + "%";
    }

    private String shortModel() {
        // Shorten long model names to keep the bar compact
        if (model.length() <= 24) return model;
        return model.substring(0, 21) + "...";
    }
}
