package cz.krokviak.agents.cli.render.tui;

import dev.tamboui.toolkit.element.Element;

import static dev.tamboui.toolkit.Toolkit.*;

/** Bottom status bar: model, context usage, cost, permission mode. Pure view. */
public final class StatusBarComponent {
    private StatusBarComponent() {}

    public static Element render(CliState state) {
        double pct = state.tokensMax() > 0
            ? (double) state.tokensUsed() / state.tokensMax() * 100 : 0;

        String status = String.format(
            "model: %s │ context: %.0f%% │ cost: $%.2f │ mode: %s",
            state.model().isEmpty() ? "—" : state.model(),
            pct, state.cost(), state.permMode());

        return text(status).dim().length(1);
    }
}
