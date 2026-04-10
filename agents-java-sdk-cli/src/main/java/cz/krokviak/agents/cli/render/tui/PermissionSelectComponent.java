package cz.krokviak.agents.cli.render.tui;

import dev.tamboui.style.Color;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.elements.ListElement;
import dev.tamboui.toolkit.event.EventResult;

import java.util.function.IntConsumer;

import static dev.tamboui.toolkit.Toolkit.*;

/**
 * Replaces the input area during permission prompts.
 * Arrow-key navigable list of options inside a bordered panel.
 */
public final class PermissionSelectComponent {
    private PermissionSelectComponent() {}

    public static Element render(String header, String[] options,
                                 ListElement<?> optionList, IntConsumer onSelect) {
        return row(
            spacer(1),
            panel(
                optionList
                    .items(options)
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
                        if (event.isChar('y')) { onSelect.accept(0); return EventResult.HANDLED; }
                        if (event.isChar('a')) { onSelect.accept(1); return EventResult.HANDLED; }
                        if (event.isChar('n')) { onSelect.accept(2); return EventResult.HANDLED; }
                        return EventResult.UNHANDLED;
                    })
            ).title(header).rounded().borderColor(Color.YELLOW).fill(),
            spacer(1)
        ).length(options.length + 2); // options + border top/bottom
    }
}
