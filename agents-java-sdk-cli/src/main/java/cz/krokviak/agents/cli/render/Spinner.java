package cz.krokviak.agents.cli.render;

/**
 * Braille-animation spinner that runs on a virtual thread.
 * <p>
 * Usage:
 * <pre>
 *   Spinner s = new Spinner();
 *   s.start("Loading…");
 *   // … do work …
 *   s.update("Still loading…");
 *   // … do more work …
 *   s.stop("Done!");
 * </pre>
 */
public final class Spinner {

    private static final String[] FRAMES = {
            "⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"
    };

    private static final String DIM   = "\033[2m";
    private static final String RESET = "\033[0m";

    private volatile Thread thread;
    private volatile boolean running;
    private volatile String label;

    /**
     * Start the spinner with an initial label.
     * If a spinner is already running it is stopped first.
     */
    public void start(String label) {
        stop(null);
        this.label   = label;
        this.running = true;
        this.thread  = Thread.startVirtualThread(() -> {
            int frame = 0;
            while (running) {
                String current = this.label;
                System.out.print("\r" + DIM + FRAMES[frame % FRAMES.length]
                        + " " + current + RESET + "  ");
                System.out.flush();
                frame++;
                try {
                    Thread.sleep(80);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            // Clear the spinner line
            String current = this.label;
            int clearLen = (current != null ? current.length() : 0) + 5;
            System.out.print("\r" + " ".repeat(clearLen) + "\r");
            System.out.flush();
        });
    }

    /**
     * Update the spinner label without stopping the animation.
     */
    public void update(String newLabel) {
        this.label = newLabel;
    }

    /**
     * Stop the spinner.
     *
     * @param finalMessage if non-null and non-empty, printed after the spinner clears
     */
    public void stop(String finalMessage) {
        running = false;
        if (thread != null) {
            thread.interrupt();
            try { thread.join(300); } catch (InterruptedException ignored) {}
            thread = null;
        }
        if (finalMessage != null && !finalMessage.isEmpty()) {
            System.out.println(finalMessage);
            System.out.flush();
        }
    }
}
