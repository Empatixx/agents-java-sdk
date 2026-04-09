package cz.krokviak.agents.cli.render.tui;

import dev.tamboui.toolkit.element.Element;

import static dev.tamboui.toolkit.Toolkit.*;

/** Shows animated spinner when active, empty otherwise. Pure view. */
public final class SpinnerBarComponent {
    private SpinnerBarComponent() {}

    public static Element render(CliState state) {
        if (!state.spinnerActive()) {
            return text("").length(0);
        }
        return row(
            spinner().cyan().fit(),
            text(" " + state.spinnerLabel()).dim().fit()
        ).length(1);
    }
}
