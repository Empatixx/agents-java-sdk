package cz.krokviak.agents.cli.render;

/**
 * Interactive prompt interface — separated from Renderer to follow ISP.
 * Not all renderers support interactive prompts (e.g. piped/non-TTY mode).
 */
public interface PromptRenderer {

    /**
     * Show a selection prompt with numbered options. Returns selected index.
     */
    int promptSelection(String header, String[] options);

    /**
     * Show a text input prompt. Returns typed text, or empty on cancel.
     */
    String promptTextInput(String header, String placeholder);

    /**
     * Consume any queued prompt text (submitted while runner was busy).
     */
    default String consumeQueuedPrompt() { return null; }
}
