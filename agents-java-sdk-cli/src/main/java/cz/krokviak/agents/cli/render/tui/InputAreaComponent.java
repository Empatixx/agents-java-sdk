package cz.krokviak.agents.cli.render.tui;

import dev.tamboui.style.Color;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.event.KeyEventHandler;
import dev.tamboui.widgets.input.TextInputState;

import static dev.tamboui.toolkit.Toolkit.*;

/** Text input area with rounded border and horizontal margin. */
public final class InputAreaComponent {
    private InputAreaComponent() {}

    public static Element render(TextInputState inputState,
                                 Runnable onSubmit, KeyEventHandler extraKeys) {
        return row(
            spacer(1),
            panel(
                textInput(inputState)
                    .placeholder("Type a message...")
                    .id("input")
                    .focusable()
                    .onSubmit(onSubmit)
                    .onKeyEvent(extraKeys)
            ).title("❯").rounded().borderColor(Color.CYAN).fill(),
            spacer(1)
        ).length(3);
    }
}
