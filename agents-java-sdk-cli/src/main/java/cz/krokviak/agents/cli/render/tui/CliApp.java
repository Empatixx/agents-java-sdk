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
 * Thin ToolkitApp shell — wires controller + view + events.
 * No business logic here.
 */
public final class CliApp extends ToolkitApp {

    private final CliController ctrl;
    private final TuiRenderer renderer;
    private final TextInputState inputState = new TextInputState();
    private final BlockingQueue<String> inputQueue = new LinkedBlockingQueue<>();
    private final CountDownLatch ready = new CountDownLatch(1);
    private final dev.tamboui.toolkit.elements.ListElement<?> permList = list();

    public CliApp(CliController ctrl, TuiRenderer renderer) {
        this.ctrl = ctrl;
        this.renderer = renderer;
    }

    @Override
    protected TuiConfig configure() {
        return TuiConfig.builder().tickRate(Duration.ofMillis(100)).build();
    }

    @Override
    protected void onStart() {
        renderer.activate(runner());
        ready.countDown();
    }

    @Override
    protected Element render() {
        return CliView.render(ctrl, inputState, permList,
            this::handleSubmit,
            event -> {
                if (event.isCtrlC()) { quit(); return EventResult.HANDLED; }
                if (event.character() == 15 || (event.hasCtrl() && event.isChar('o'))) {
                    renderer.toggleExpand();
                    return EventResult.HANDLED;
                }
                var suggestions = ctrl.suggestCommands(inputState.text());
                if (event.isChar('\t') && !suggestions.isEmpty()) {
                    inputState.setText("/" + suggestions.getFirst().command());
                    inputState.moveCursorToEnd();
                    return EventResult.HANDLED;
                }
                return EventResult.UNHANDLED;
            },
            renderer::resolvePermission
        );
    }

    private void handleSubmit() {
        String text = inputState.text().trim();
        if (text.isEmpty()) return;
        runner().runOnRenderThread(() -> {
            inputState.clear();
            if (renderer.hasPermissionPrompt()) {
                int sel = switch (text.toLowerCase()) {
                    case "1","y","yes" -> 0; case "2","a","always" -> 1;
                    case "3","n","no" -> 2;
                    default -> { try { yield Integer.parseInt(text)-1; } catch (Exception e) { yield 2; } }
                };
                renderer.resolvePermission(sel);
                return;
            }
            ctrl.addLine(new OutputLine.UserMessage(text));
        });
        if (!renderer.hasPermissionPrompt()) inputQueue.offer(text);
    }

    public String readLine() throws InterruptedException { return inputQueue.take(); }
    public void awaitReady() throws InterruptedException { ready.await(); }
}
