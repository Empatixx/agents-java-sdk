package cz.krokviak.agents.cli.render.tui;

import dev.tamboui.toolkit.app.ToolkitApp;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.TuiConfig;
import dev.tamboui.widgets.input.TextInputState;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;

import static dev.tamboui.toolkit.Toolkit.*;

/**
 * Thin ToolkitApp shell. Composes components, handles input submission.
 * All state lives in {@link CliState}, all rendering logic in components.
 */
public final class CliApp extends ToolkitApp {

    private final CliState state;
    private final TuiRenderer tuiRenderer;
    private final TextInputState inputState = new TextInputState();
    private final BlockingQueue<String> inputQueue = new LinkedBlockingQueue<>();
    private final CountDownLatch ready = new CountDownLatch(1);

    public CliApp(CliState state, TuiRenderer tuiRenderer) {
        this.state = state;
        this.tuiRenderer = tuiRenderer;
    }

    @Override
    protected TuiConfig configure() {
        return TuiConfig.builder()
            .tickRate(Duration.ofMillis(100))
            .build();
    }

    @Override
    protected void onStart() {
        tuiRenderer.activate(runner());
        ready.countDown();
    }

    @Override
    protected Element render() {
        // Command suggestions based on current input
        String input = inputState.text();
        List<CommandTrie.Match> suggestions = state.suggestCommands(input);

        // Ghost suffix: remaining chars of first match after what user typed
        String ghostSuffix = null;
        if (!suggestions.isEmpty() && input.startsWith("/") && input.length() > 1) {
            String firstCmd = suggestions.getFirst().command();
            String typed = input.substring(1); // strip "/"
            if (firstCmd.startsWith(typed) && firstCmd.length() > typed.length()) {
                ghostSuffix = firstCmd.substring(typed.length());
            }
        }

        return column(
            OutputLogComponent.render(state),
            SpinnerBarComponent.render(state),
            InputAreaComponent.render(inputState, ghostSuffix, this::handleSubmit, event -> {
                if (event.isCtrlC()) { quit(); return EventResult.HANDLED; }
                if (event.character() == 15) { // Ctrl+O
                    tuiRenderer.toggleExpandCollapse();
                    return EventResult.HANDLED;
                }
                // Tab completion: if suggestions, fill first match
                if (event.isChar('\t') && !suggestions.isEmpty()) {
                    inputState.setText("/" + suggestions.getFirst().command());
                    inputState.moveCursorToEnd();
                    return EventResult.HANDLED;
                }
                return EventResult.UNHANDLED;
            }),
            InfoPanelComponent.render(state, suggestions)
        );
    }

    private void handleSubmit() {
        String text = inputState.text().trim();
        if (!text.isEmpty()) {
            inputQueue.offer(text);
            runner().runOnRenderThread(inputState::clear);
        }
    }

    /** Blocking read for the REPL. */
    public String readLine() throws InterruptedException {
        return inputQueue.take();
    }

    /** Block until ToolkitApp is initialized. */
    public void awaitReady() throws InterruptedException {
        ready.await();
    }
}
