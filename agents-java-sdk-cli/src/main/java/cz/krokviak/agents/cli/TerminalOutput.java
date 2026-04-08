package cz.krokviak.agents.cli;

import java.util.Map;

public class TerminalOutput {

    static final String RESET = "\033[0m";
    static final String CYAN = "\033[36m";
    static final String DIM = "\033[2m";
    static final String RED = "\033[31m";
    static final String GREEN = "\033[32m";
    static final String BOLD = "\033[1m";
    static final String YELLOW = "\033[33m";
    static final String MAGENTA = "\033[35m";

    private static final String[] SPINNER_FRAMES = {"⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"};

    private volatile Thread spinnerThread;
    private volatile boolean spinning;

    // --- Spinner ---

    public void startSpinner(String message) {
        spinning = true;
        spinnerThread = Thread.startVirtualThread(() -> {
            int frame = 0;
            while (spinning) {
                System.out.print("\r" + DIM + SPINNER_FRAMES[frame % SPINNER_FRAMES.length] + " " + message + RESET + "  ");
                System.out.flush();
                frame++;
                try { Thread.sleep(80); } catch (InterruptedException e) { break; }
            }
            System.out.print("\r" + " ".repeat(message.length() + 5) + "\r");
            System.out.flush();
        });
    }

    public void stopSpinner() {
        spinning = false;
        if (spinnerThread != null) {
            spinnerThread.interrupt();
            try { spinnerThread.join(200); } catch (InterruptedException ignored) {}
            spinnerThread = null;
        }
    }

    // --- Tool Display ---

    public void printToolCall(String name, Map<String, Object> args) {
        System.out.println();
        System.out.println(CYAN + "╭─ " + BOLD + name + RESET);
        if (args != null) {
            for (var entry : args.entrySet()) {
                String value = truncate(String.valueOf(entry.getValue()), 200);
                System.out.println(CYAN + "│  " + DIM + entry.getKey() + ": " + RESET + value);
            }
        }
        System.out.println(CYAN + "╰─────" + RESET);
    }

    public void printToolResult(String name, String output) {
        if (output == null || output.isEmpty()) return;
        String[] lines = output.split("\n", -1);
        int maxLines = 50;
        System.out.print(DIM);
        for (int i = 0; i < Math.min(lines.length, maxLines); i++) {
            System.out.println(colorizeResultLine(lines[i]));
        }
        if (lines.length > maxLines) {
            System.out.println("... (" + (lines.length - maxLines) + " more lines)");
        }
        System.out.print(RESET);
    }

    public void printToolTiming(long startNanos) {
        long elapsed = System.nanoTime() - startNanos;
        double seconds = elapsed / 1_000_000_000.0;
        System.out.println(DIM + "  completed in " + String.format("%.1fs", seconds) + RESET);
    }

    // --- Text Output ---

    public void printTextDelta(String delta) {
        System.out.print(delta);
    }

    public void printError(String message) {
        System.out.println(RED + "✗ " + message + RESET);
    }

    public void printPrompt() {
        System.out.println();
        System.out.print(BOLD + "you> " + RESET);
        System.out.flush();
    }

    public void printPromptWithCost(String costInfo) {
        System.out.println();
        System.out.print(BOLD + "you" + RESET + DIM + " " + costInfo + RESET + BOLD + "> " + RESET);
        System.out.flush();
    }

    public void printUsage(String formatted) {
        System.out.println(DIM + formatted + RESET);
    }

    public void println(String text) {
        System.out.println(text);
    }

    public void printPermissionDenied(String toolName) {
        System.out.println(YELLOW + "⚠ Permission denied for " + toolName + RESET);
    }

    // --- Diff / Code Block Coloring ---

    private String colorizeResultLine(String line) {
        if (line.startsWith("+") && !line.startsWith("+++")) {
            return GREEN + line + DIM;
        } else if (line.startsWith("-") && !line.startsWith("---")) {
            return RED + line + DIM;
        } else if (line.startsWith("@@")) {
            return CYAN + line + DIM;
        }
        return line;
    }

    // --- Utility ---

    private static String truncate(String s, int max) {
        if (s.length() <= max) return s;
        return s.substring(0, max) + "...";
    }
}
