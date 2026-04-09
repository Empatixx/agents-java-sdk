package cz.krokviak.agents.cli.render.tui;

import dev.tamboui.toolkit.app.ToolkitApp;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.TuiConfig;
import dev.tamboui.widgets.input.TextInputState;

import java.time.Duration;
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
        return column(
            OutputLogComponent.render(state),
            SpinnerBarComponent.render(state),
            InputAreaComponent.render(inputState, this::handleSubmit, event -> {
                if (event.isCtrlC()) { quit(); return EventResult.HANDLED; }
                if (event.character() == 15) { // Ctrl+O
                    tuiRenderer.toggleExpandCollapse();
                    return EventResult.HANDLED;
                }
                return EventResult.UNHANDLED;
            }),
            StatusBarComponent.render(state)
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
