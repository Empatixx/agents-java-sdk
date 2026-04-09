package cz.krokviak.agents.cli.render.tui;

import dev.tamboui.style.Color;
import dev.tamboui.toolkit.app.InlineApp;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.event.EventResult;

import static dev.tamboui.toolkit.Toolkit.*;

/**
 * Standalone InlineApp that prompts the user to select from a list of options.
 * Blocks until a selection is made. Returns the selected index.
 */
public final class PermissionDialog {
    private PermissionDialog() {}

    /** Blocking prompt. Returns index of selected option. */
    public static int prompt(String header, String[] options) {
        int[] result = { options.length - 1 }; // default: last option (deny)

        try {
            new InlineApp() {
                private final dev.tamboui.toolkit.elements.ListElement<?> optionList = list(options)
                    .highlightColor(Color.CYAN)
                    .highlightSymbol("❯ ");

                @Override
                protected int height() {
                    return options.length + 2;
                }

                @Override
                protected Element render() {
                    return column(
                        text(header).bold().yellow(),
                        optionList
                            .id("options")
                            .focusable()
                            .onKeyEvent(event -> {
                                if (event.isConfirm()) {
                                    result[0] = optionList.selected();
                                    quit();
                                    return EventResult.HANDLED;
                                }
                                if (event.isUp()) {
                                    optionList.selected(Math.max(0, optionList.selected() - 1));
                                    return EventResult.HANDLED;
                                }
                                if (event.isDown()) {
                                    optionList.selected(Math.min(options.length - 1, optionList.selected() + 1));
                                    return EventResult.HANDLED;
                                }
                                if (event.isChar('y')) { result[0] = 0; quit(); return EventResult.HANDLED; }
                                if (event.isChar('a')) { result[0] = 1; quit(); return EventResult.HANDLED; }
                                if (event.isChar('n') || event.isCtrlC()) {
                                    result[0] = options.length - 1; quit(); return EventResult.HANDLED;
                                }
                                return EventResult.UNHANDLED;
                            })
                    );
                }
            }.run();
        } catch (Exception e) {
            return options.length - 1; // deny on error
        }

        return result[0];
    }
}
