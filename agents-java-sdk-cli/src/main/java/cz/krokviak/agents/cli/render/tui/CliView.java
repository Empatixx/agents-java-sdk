package cz.krokviak.agents.cli.render.tui;

import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.elements.ListElement;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.toolkit.event.KeyEventHandler;
import dev.tamboui.widgets.input.TextInputState;

import java.util.List;

import static dev.tamboui.toolkit.Toolkit.*;

/**
 * Pure view — reads from CliController, returns Element tree. No side effects.
 */
public final class CliView {
    private CliView() {}

    public static Element render(CliController ctrl, TextInputState inputState,
                                 ListElement<?> permissionList,
                                 Runnable onSubmit, KeyEventHandler inputKeys,
                                 java.util.function.IntConsumer onPermissionSelect) {
        // Permission mode
        if (ctrl.hasPermissionPrompt()) {
            return column(
                OutputLogComponent.render(ctrl),
                SpinnerBarComponent.render(ctrl),
                PermissionSelectComponent.render(
                    ctrl.permissionHeader(), ctrl.permissionOptions(),
                    permissionList, onPermissionSelect),
                InfoPanelComponent.render(ctrl, List.of())
            );
        }

        // Normal mode
        List<CommandTrie.Match> suggestions = ctrl.suggestCommands(inputState.text());

        return column(
            OutputLogComponent.render(ctrl),
            SpinnerBarComponent.render(ctrl),
            InputAreaComponent.render(inputState, onSubmit, inputKeys),
            InfoPanelComponent.render(ctrl, suggestions)
        );
    }
}
