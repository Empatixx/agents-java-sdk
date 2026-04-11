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
    private Runnable planModeToggle;
    private dev.tamboui.tui.bindings.Bindings appBindings;
    private final java.util.List<String> inputHistory = new java.util.ArrayList<>();
    private int historyIndex = -1;
    private volatile boolean runnerBusy;

    public CliApp(CliController ctrl, TuiRenderer renderer) {
        this.ctrl = ctrl;
        this.renderer = renderer;
    }

    public void setPlanModeToggle(Runnable toggle) { this.planModeToggle = toggle; }

    private void togglePlanMode() {
        if (planModeToggle != null) planModeToggle.run();
    }

    @Override
    protected TuiConfig configure() {
        appBindings = dev.tamboui.tui.bindings.BindingSets.standard().toBuilder()
            // Remove Tab from focus navigation — we have one input, don't need focus cycling
            .unbind(dev.tamboui.tui.bindings.Actions.FOCUS_NEXT)
            .unbind(dev.tamboui.tui.bindings.Actions.FOCUS_PREVIOUS)
            // Bind Shift+Tab to plan mode
            .bind(dev.tamboui.tui.bindings.KeyTrigger.key(
                dev.tamboui.tui.event.KeyCode.TAB, true, false, false), "planMode")
            .bind(dev.tamboui.tui.bindings.KeyTrigger.ctrl('o'), "expandCollapse")
            .build();
        return TuiConfig.builder()
            .tickRate(Duration.ofMillis(100))
            .bindings(appBindings)
            .build();
    }

    @Override
    protected void onStart() {
        renderer.activate(runner());

        // Blink tick for agent ● animation
        runner().scheduleRepeating(() ->
            runner().runOnRenderThread(() -> OutputLine.TICK.incrementAndGet()),
            Duration.ofMillis(500));

        runner().eventRouter().addGlobalHandler(event -> {
            if (!(event instanceof dev.tamboui.tui.event.KeyEvent key)) return EventResult.UNHANDLED;
            // Check binding actions (Shift+Tab → "planMode", Ctrl+O → "expandCollapse")
            var action = key.action();
            if (action.isPresent()) {
                return switch (action.get()) {
                    case "planMode" -> { togglePlanMode(); yield EventResult.HANDLED; }
                    case "expandCollapse" -> { renderer.toggleExpand(); yield EventResult.HANDLED; }
                    default -> EventResult.UNHANDLED;
                };
            }
            if (!key.hasCtrl()) return EventResult.UNHANDLED;
            if (key.character() == 'g') { togglePlanMode(); return EventResult.HANDLED; }
            if (key.character() == 'o') { renderer.toggleExpand(); return EventResult.HANDLED; }
            return EventResult.UNHANDLED;
        });

        ready.countDown();
    }

    @Override
    protected Element render() {
        return CliView.render(ctrl, inputState, permList,
            this::handleSubmit,
            event -> {
                // Tab completion for /commands
                if (event.isKey(dev.tamboui.tui.event.KeyCode.TAB)) {
                    String text = inputState.text();
                    if (text != null && text.startsWith("/") && text.length() >= 2) {
                        var suggestions = ctrl.suggestCommands(text);
                        if (!suggestions.isEmpty()) {
                            inputState.setText("/" + suggestions.getFirst().command());
                        }
                    }
                    return EventResult.HANDLED;
                }
                // History navigation
                if (event.isUp() && !inputHistory.isEmpty()) {
                    if (historyIndex > 0) historyIndex--;
                    inputState.setText(inputHistory.get(historyIndex));
                    return EventResult.HANDLED;
                }
                if (event.isDown()) {
                    if (historyIndex < inputHistory.size() - 1) {
                        historyIndex++;
                        inputState.setText(inputHistory.get(historyIndex));
                    } else {
                        historyIndex = inputHistory.size();
                        inputState.clear();
                    }
                    return EventResult.HANDLED;
                }
                return EventResult.UNHANDLED;
            },
            renderer::resolvePermission,
            renderer::resolveTextInput
        );
    }

    private void handleSubmit() {
        String text = inputState.text().trim();
        if (text.isEmpty()) return;
        inputHistory.add(text);
        historyIndex = inputHistory.size();
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
        if (!renderer.hasPermissionPrompt()) {
            if (runnerBusy) {
                // Runner is active — queue the prompt, show it above input
                runner().runOnRenderThread(() -> ctrl.appendQueuedPrompt(text));
            } else {
                inputQueue.offer(text);
            }
        }
    }

    public void setRunnerBusy(boolean busy) { this.runnerBusy = busy; }

    public String readLine() throws InterruptedException { return inputQueue.take(); }
    public void awaitReady() throws InterruptedException { ready.await(); }

    /**
     * Drain all queued inputs (non-blocking) and concatenate them.
     * Returns null if nothing queued.
     */
    public String drainQueued() {
        java.util.List<String> queued = new java.util.ArrayList<>();
        inputQueue.drainTo(queued);
        if (queued.isEmpty()) return null;
        return String.join("\n", queued);
    }
}
