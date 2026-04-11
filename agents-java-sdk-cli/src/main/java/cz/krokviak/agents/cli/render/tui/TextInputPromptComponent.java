package cz.krokviak.agents.cli.render.tui;

import dev.tamboui.style.Color;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.widgets.input.TextInputState;

import java.util.function.Consumer;

import static dev.tamboui.toolkit.Toolkit.*;

/**
 * Text input prompt — bold header inside panel with a text field below.
 * User types text and presses Enter to submit.
 */
public final class TextInputPromptComponent {
    private TextInputPromptComponent() {}

    public static Element render(String header, String placeholder,
                                 TextInputState inputState, Consumer<String> onSubmit) {
        return row(
            spacer(1),
            panel(
                row(spacer(1), column(
                    text(header).bold(),
                    text(""),
                    textInput(inputState)
                        .placeholder(placeholder != null ? placeholder : "Type here...")
                        .focusable()
                        .onSubmit(() -> {
                            String value = inputState.text().trim();
                            onSubmit.accept(value);
                        })
                ), spacer(1))
            ).rounded().borderColor(Color.YELLOW).fill(),
            spacer(1)
        ).length(5);
    }
}
