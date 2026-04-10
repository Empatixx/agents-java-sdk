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

        var actions = new dev.tamboui.tui.bindings.ActionHandler(appBindings);
        actions.on("planMode", _ -> togglePlanMode());
        actions.on("expandCollapse", _ -> renderer.toggleExpand());
        runner().eventRouter().addGlobalHandler(actions);

        ready.countDown();
    }

    @Override
    protected Element render() {
        return CliView.render(ctrl, inputState, permList,
            this::handleSubmit,
            event -> EventResult.UNHANDLED,
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
