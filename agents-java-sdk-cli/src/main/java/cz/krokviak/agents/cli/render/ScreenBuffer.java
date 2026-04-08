package cz.krokviak.agents.cli.render;

import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.IOException;
import java.io.PrintWriter;

/**
 * Double-buffered 2D character grid for efficient terminal rendering.
 *
 * <p>Each cell stores a character, a foreground style (ANSI escape sequence string), and a
 * background style. On {@link #flush()}, only cells that differ from the previous frame are
 * re-drawn using ANSI cursor-positioning sequences, minimising flicker.</p>
 *
 * <p>Instances are NOT thread-safe; callers must synchronise externally.</p>
 */
public final class ScreenBuffer {

    // ANSI escape helpers
    private static final String CSI   = "\033[";
    private static final String RESET = "\033[0m";

    private final Terminal terminal;
    private final PrintWriter out;

    // Current (back) buffer being written into
    private char[][]   curChars;
    private String[][] curStyle;   // combined ANSI style prefix per cell

    // Previous (front) buffer — what is already on screen
    private char[][]   prevChars;
    private String[][] prevStyle;

    private int rows;
    private int cols;

    public ScreenBuffer() {
        this(buildTerminal());
    }

    public ScreenBuffer(Terminal terminal) {
        this.terminal = terminal;
        this.out      = terminal.writer();
        int[] size    = terminalSize();
        this.rows     = size[0];
        this.cols     = size[1];
        allocate(rows, cols);
    }

    private static Terminal buildTerminal() {
        try {
            return TerminalBuilder.builder()
                    .system(true)
                    .dumb(true)
                    .build();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot open terminal", e);
        }
    }

    // ------------------------------------------------------------------
    //  Size
    // ------------------------------------------------------------------

    /** Returns current [rows, cols] from the underlying JLine terminal. */
    public int[] terminalSize() {
        org.jline.terminal.Size size = terminal.getSize();
        int r = size.getRows();
        int c = size.getColumns();
        if (r <= 0) r = 24;
        if (c <= 0) c = 80;
        return new int[]{r, c};
    }

    public int rows() { return rows; }
    public int cols() { return cols; }

    /** Resize the buffer (existing content is lost). */
    public void resize(int newRows, int newCols) {
        this.rows = newRows;
        this.cols = newCols;
        allocate(newRows, newCols);
    }

    /** Sync size from the terminal, resizing if necessary. */
    public boolean syncSize() {
        int[] s = terminalSize();
        if (s[0] != rows || s[1] != cols) {
            resize(s[0], s[1]);
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------
    //  Writing
    // ------------------------------------------------------------------

    /**
     * Write {@code text} starting at (row, col) with the given ANSI style prefix.
     * Characters that fall outside the buffer dimensions are silently clipped.
     *
     * @param row   0-based row index
     * @param col   0-based column index
     * @param text  text to write (multi-character strings are placed left-to-right)
     * @param style ANSI escape sequence(s) to apply (e.g. {@code "\033[32m"}) or empty string
     */
    public void write(int row, int col, String text, String style) {
        if (row < 0 || row >= rows || text == null) return;
        String s = style == null ? "" : style;
        for (int i = 0; i < text.length(); i++) {
            int c = col + i;
            if (c < 0 || c >= cols) continue;
            curChars[row][c] = text.charAt(i);
            curStyle[row][c] = s;
        }
    }

    /**
     * Write {@code text} at (row, 0), padding the remainder of the line with spaces
     * using the same style. Useful for full-line rendering such as the status bar.
     */
    public void writeLine(int row, String text, String style) {
        if (row < 0 || row >= rows) return;
        String s = style == null ? "" : style;
        int len = Math.min(text.length(), cols);
        for (int c = 0; c < len; c++) {
            curChars[row][c] = text.charAt(c);
            curStyle[row][c] = s;
        }
        // Pad the rest of the line
        for (int c = len; c < cols; c++) {
            curChars[row][c] = ' ';
            curStyle[row][c] = s;
        }
    }

    // ------------------------------------------------------------------
    //  Clear
    // ------------------------------------------------------------------

    /** Fill the current buffer with spaces (no style). */
    public void clear() {
        for (int r = 0; r < rows; r++) {
            java.util.Arrays.fill(curChars[r], ' ');
            java.util.Arrays.fill(curStyle[r], "");
        }
    }

    /** Clear a single row. */
    public void clearRow(int row) {
        if (row < 0 || row >= rows) return;
        java.util.Arrays.fill(curChars[row], ' ');
        java.util.Arrays.fill(curStyle[row], "");
    }

    // ------------------------------------------------------------------
    //  Flush
    // ------------------------------------------------------------------

    /**
     * Compare the current buffer against the previous frame and emit ANSI sequences
     * only for changed cells. After flushing, the current buffer becomes the previous frame.
     */
    public void flush() {
        StringBuilder sb = new StringBuilder();

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                char ch   = curChars[r][c];
                String st = curStyle[r][c];
                if (ch == prevChars[r][c] && st.equals(prevStyle[r][c])) {
                    continue; // unchanged cell — skip
                }
                // Move cursor to (r+1, c+1) — ANSI rows/cols are 1-based
                sb.append(CSI).append(r + 1).append(';').append(c + 1).append('H');
                if (!st.isEmpty()) sb.append(st);
                sb.append(ch);
                if (!st.isEmpty()) sb.append(RESET);
            }
        }

        if (!sb.isEmpty()) {
            out.print(sb);
            out.flush();
        }

        // Swap buffers
        copyTo(curChars, prevChars);
        copyTo(curStyle, prevStyle);
    }

    /** Move the real terminal cursor to (row, col) (0-based). */
    public void moveCursor(int row, int col) {
        out.print(CSI + (row + 1) + ";" + (col + 1) + "H");
        out.flush();
    }

    /** Hide terminal cursor. */
    public void hideCursor() {
        out.print(CSI + "?25l");
        out.flush();
    }

    /** Show terminal cursor. */
    public void showCursor() {
        out.print(CSI + "?25h");
        out.flush();
    }

    /** Erase entire display and move cursor to home. */
    public void eraseDisplay() {
        out.print(CSI + "2J" + CSI + "H");
        out.flush();
    }

    /** Close the underlying terminal, restoring its state. */
    public void close() {
        try {
            terminal.close();
        } catch (IOException ignored) {
        }
    }

    // ------------------------------------------------------------------
    //  Private helpers
    // ------------------------------------------------------------------

    private void allocate(int r, int c) {
        curChars  = new char[r][c];
        curStyle  = new String[r][c];
        prevChars = new char[r][c];
        prevStyle = new String[r][c];
        for (int i = 0; i < r; i++) {
            java.util.Arrays.fill(curChars[i],  ' ');
            java.util.Arrays.fill(curStyle[i],  "");
            java.util.Arrays.fill(prevChars[i], '\0'); // force full repaint on first flush
            java.util.Arrays.fill(prevStyle[i], "");
        }
    }

    private static void copyTo(char[][] src, char[][] dst) {
        for (int i = 0; i < src.length; i++) {
            System.arraycopy(src[i], 0, dst[i], 0, src[i].length);
        }
    }

    private static void copyTo(String[][] src, String[][] dst) {
        for (int i = 0; i < src.length; i++) {
            System.arraycopy(src[i], 0, dst[i], 0, src[i].length);
        }
    }
}
