package cz.krokviak.agents.cli.render.tui;

import dev.tamboui.style.Color;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.event.KeyEventHandler;
import dev.tamboui.widgets.input.TextInputState;

import static dev.tamboui.toolkit.Toolkit.*;

/** Text input with inline ghost suggestion and horizontal margin. */
public final class InputAreaComponent {
    private InputAreaComponent() {}

    public static Element render(TextInputState inputState, String ghostSuffix,
                                 Runnable onSubmit, KeyEventHandler extraKeys) {
        var input = textInput(inputState)
            .placeholder("Type a message...")
            .id("input")
            .focusable()
            .onSubmit(onSubmit)
            .onKeyEvent(extraKeys);

        Element content;
        if (ghostSuffix != null && !ghostSuffix.isEmpty()) {
            // Show ghost completion after cursor as dim text
            content = row(
                input.fill(),
                text(ghostSuffix).dim().fit()
            );
        } else {
            content = input;
        }

        return row(
            spacer(1),
            panel(content).title("❯").rounded().borderColor(Color.CYAN).fill(),
            spacer(1)
        ).length(3);
    }
}
