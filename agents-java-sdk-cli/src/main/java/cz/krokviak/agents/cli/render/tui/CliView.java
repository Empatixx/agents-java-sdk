package cz.krokviak.agents.cli.render.tui;

import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.elements.ListElement;
import dev.tamboui.toolkit.event.KeyEventHandler;
import dev.tamboui.widgets.input.TextInputState;

import java.util.ArrayList;
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
                                 java.util.function.IntConsumer onPermissionSelect,
                                 java.util.function.Consumer<String> onTextInputSubmit) {
        // Text input prompt mode
        if (ctrl.hasTextInputPrompt()) {
            return column(
                OutputLogComponent.render(ctrl),
                SpinnerBarComponent.render(ctrl),
                TextInputPromptComponent.render(
                    ctrl.textInputHeader(), ctrl.textInputPlaceholder(),
                    ctrl.textInputState(), onTextInputSubmit),
                InfoPanelComponent.render(ctrl, List.of())
            );
        }

        // Multi-question mode
        if (ctrl.hasMultiQuestions()) {
            return column(
                OutputLogComponent.render(ctrl),
                SpinnerBarComponent.render(ctrl),
                MultiQuestionComponent.render(ctrl, () -> onPermissionSelect.accept(-1)),
                InfoPanelComponent.render(ctrl, List.of())
            );
        }

        // Permission/selector mode
        if (ctrl.hasPermissionPrompt()) {
            return column(
                OutputLogComponent.render(ctrl),
                SpinnerBarComponent.render(ctrl),
                PermissionSelectComponent.render(
                    ctrl.permissionHeader(), ctrl.permissionOptions(),
                    permissionList, onPermissionSelect, ctrl),
                InfoPanelComponent.render(ctrl, List.of())
            );
        }

        // Normal mode
        List<CommandTrie.Match> suggestions = ctrl.suggestCommands(inputState.text());

        var elements = new ArrayList<Element>();
        elements.add(OutputLogComponent.render(ctrl));
        elements.add(SpinnerBarComponent.render(ctrl));

        // Queued prompt bar above input
        if (ctrl.hasQueuedPrompt()) {
            String preview = ctrl.queuedPrompt().replace('\n', ' ');
            if (preview.length() > 70) preview = preview.substring(0, 70) + "...";
            elements.add(row(spacer(1), text(" \u23f3 " + preview).dim().italic(), spacer(1)));
        }

        elements.add(InputAreaComponent.render(inputState, ctrl.isPlanMode(), onSubmit, inputKeys));
        elements.add(InfoPanelComponent.render(ctrl, suggestions));

        return column(elements.toArray(Element[]::new));
    }
}
