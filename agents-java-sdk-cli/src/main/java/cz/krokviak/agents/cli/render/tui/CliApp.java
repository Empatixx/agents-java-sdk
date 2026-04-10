package cz.krokviak.agents.cli.render.tui;

import dev.tamboui.toolkit.app.ToolkitApp;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.elements.ListElement;
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

    // Reusable list element for permission selection (avoids recreating each render)
    private final ListElement<?> permissionList = list();

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
        // Permission prompt active → swap input area for selection list
        if (state.hasPermissionPrompt()) {
            return column(
                OutputLogComponent.render(state),
                SpinnerBarComponent.render(state),
                PermissionSelectComponent.render(
                    state.permissionHeader(),
                    state.permissionOptions(),
                    permissionList,
                    tuiRenderer::resolvePermission
                ),
                InfoPanelComponent.render(state, List.of())
            );
        }

        // Normal mode
        String input = inputState.text();
        List<CommandTrie.Match> suggestions = state.suggestCommands(input);

        return column(
            OutputLogComponent.render(state),
            SpinnerBarComponent.render(state),
            InputAreaComponent.render(inputState, this::handleSubmit, event -> {
                if (event.isCtrlC()) { quit(); return EventResult.HANDLED; }
                // Ctrl+O: toggle expand/collapse
                if (event.character() == 15 || (event.hasCtrl() && event.isChar('o'))) {
                    tuiRenderer.toggleExpandCollapse();
                    return EventResult.HANDLED;
                }
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
        if (text.isEmpty()) return;

        runner().runOnRenderThread(() -> {
            inputState.clear();

            // If permission prompt active, resolve it
            if (tuiRenderer.hasPermissionPrompt()) {
                int selection = switch (text.toLowerCase()) {
                    case "1", "y", "yes" -> 0;
                    case "2", "a", "always" -> 1;
                    case "3", "n", "no" -> 2;
                    default -> {
                        try { yield Integer.parseInt(text) - 1; }
                        catch (NumberFormatException e) { yield 2; }
                    }
                };
                tuiRenderer.resolvePermission(selection);
                return;
            }

            // Show user message in output log
            OutputLogComponent.instance().add(row(
                spacer(2),
                text("❯ ").bold().cyan().fit(),
                text(text).bold().fit()
            ));
        });

        if (!tuiRenderer.hasPermissionPrompt()) {
            inputQueue.offer(text);
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
