package cz.krokviak.agents.cli.render.tui;

import dev.tamboui.toolkit.element.Element;

import static dev.tamboui.toolkit.Toolkit.*;

public final class SpinnerBarComponent {
    private SpinnerBarComponent() {}

    public static Element render(CliController ctrl) {
        if (!ctrl.spinnerActive()) return text("").length(0);
        return row(spinner().cyan().fit(), text(" " + ctrl.spinnerLabel()).dim().fit()).length(1);
    }
}
