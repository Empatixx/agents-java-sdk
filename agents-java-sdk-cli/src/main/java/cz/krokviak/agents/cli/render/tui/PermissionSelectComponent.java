package cz.krokviak.agents.cli.render.tui;

import dev.tamboui.style.Color;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.elements.ListElement;
import dev.tamboui.toolkit.event.EventResult;

import java.util.function.IntConsumer;

import static dev.tamboui.toolkit.Toolkit.*;

/**
 * Replaces the input area during permission/question prompts.
 * Numbered vertical list with arrow-key navigation.
 */
public final class PermissionSelectComponent {
    private PermissionSelectComponent() {}

    public static Element render(String header, String[] options,
                                 ListElement<?> optionList, IntConsumer onSelect) {
        // Build numbered options as formatted strings
        String[] numbered = new String[options.length];
        for (int i = 0; i < options.length; i++) {
            numbered[i] = (i + 1) + ". " + options[i];
        }

        return row(
            spacer(1),
            panel(
                optionList
                    .items(numbered)
                    .highlightColor(Color.YELLOW)
                    .highlightSymbol("❯ ")
                    .focusable()
                    .onKeyEvent(event -> {
                        if (event.isConfirm()) {
                            onSelect.accept(optionList.selected());
                            return EventResult.HANDLED;
                        }
                        if (event.isUp()) {
                            int sel = optionList.selected();
                            optionList.selected(Math.max(0, sel - 1));
                            return EventResult.HANDLED;
                        }
                        if (event.isDown()) {
                            int sel = optionList.selected();
                            optionList.selected(Math.min(options.length - 1, sel + 1));
                            return EventResult.HANDLED;
                        }
                        // Number shortcuts
                        for (int i = 0; i < options.length && i < 9; i++) {
                            if (event.isChar((char) ('1' + i))) {
                                onSelect.accept(i);
                                return EventResult.HANDLED;
                            }
                        }
                        if (event.isChar('y')) { onSelect.accept(0); return EventResult.HANDLED; }
                        if (event.isChar('n')) { onSelect.accept(Math.min(2, options.length - 1)); return EventResult.HANDLED; }
                        return EventResult.UNHANDLED;
                    })
            ).title(header).rounded().borderColor(Color.YELLOW).fill(),
            spacer(1)
        ).length(options.length + 2);
    }
}
