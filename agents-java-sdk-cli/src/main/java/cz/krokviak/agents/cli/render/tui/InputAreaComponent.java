package cz.krokviak.agents.cli.render.tui;

import dev.tamboui.style.Color;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.event.KeyEventHandler;
import dev.tamboui.widgets.input.TextInputState;

import static dev.tamboui.toolkit.Toolkit.*;

public final class InputAreaComponent {
    private InputAreaComponent() {}

    public static Element render(TextInputState inputState, boolean planMode,
                                 Runnable onSubmit, KeyEventHandler extraKeys) {
        Color borderColor = planMode ? Color.YELLOW : Color.CYAN;
        String title = planMode ? "plan" : "❯";
        return row(
            spacer(1),
            panel(
                textInput(inputState)
                    .placeholder(planMode ? "Plan mode — read-only..." : "Type a message...")
                    .focusable()
                    .onSubmit(onSubmit)
                    .onKeyEvent(extraKeys)
            ).title(title).rounded().borderColor(borderColor).fill(),
            spacer(1)
        ).length(3);
    }
}
