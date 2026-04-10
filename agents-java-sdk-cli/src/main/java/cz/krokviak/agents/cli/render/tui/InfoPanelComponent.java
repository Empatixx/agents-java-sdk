package cz.krokviak.agents.cli.render.tui;

import dev.tamboui.toolkit.element.Element;

import java.util.List;

import static dev.tamboui.toolkit.Toolkit.*;

/**
 * Fixed-height panel below input. Priority:
 * 1. Command suggestions (when typing "/")
 * 2. Agent activity (when agent is running — last 5 tool calls)
 * 3. Status info (model, cost, context)
 *
 * Always exactly PANEL_HEIGHT lines — no layout jumping.
 */
public final class InfoPanelComponent {
    private InfoPanelComponent() {}

    private static final int PANEL_HEIGHT = 5;

    public static Element render(CliState state, List<CommandTrie.Match> suggestions) {
        if (!suggestions.isEmpty()) {
            return renderSuggestions(suggestions);
        }
        if (state.activeAgentName() != null) {
            return renderAgentActivity(state);
        }
        return renderStatus(state);
    }

    private static Element renderSuggestions(List<CommandTrie.Match> suggestions) {
        var rows = new Element[PANEL_HEIGHT];
        for (int i = 0; i < PANEL_HEIGHT; i++) {
            if (i < suggestions.size()) {
                var match = suggestions.get(i);
                rows[i] = row(
                    spacer(2),
                    text("/" + match.command()).dim().bold().fit(),
                    spacer(2),
                    text(match.description()).dim().fit(),
                    spacer()
                );
            } else {
                rows[i] = text("");
            }
        }
        return column(rows).length(PANEL_HEIGHT);
    }

    private static Element renderAgentActivity(CliState state) {
        var rows = new Element[PANEL_HEIGHT];
        // Line 0: agent header with progress detail
        String detail = state.agentDetail().isEmpty() ? "running..." : state.agentDetail();
        rows[0] = row(
            spacer(2),
            spinner().cyan().fit(),
            text(" " + state.activeAgentName()).bold().cyan().fit(),
            text("  " + detail).dim().fit(),
            spacer()
        );
        // Lines 1-4: last tool calls
        List<String> calls = state.agentToolCalls();
        for (int i = 1; i < PANEL_HEIGHT; i++) {
            int callIdx = i - 1;
            if (callIdx < calls.size()) {
                rows[i] = row(spacer(4), text(calls.get(callIdx)).dim().fit(), spacer());
            } else {
                rows[i] = text("");
            }
        }
        return column(rows).length(PANEL_HEIGHT);
    }

    private static Element renderStatus(CliState state) {
        double pct = state.tokensMax() > 0
            ? (double) state.tokensUsed() / state.tokensMax() * 100 : 0;
        String modelName = state.model().isEmpty() ? "—" : state.model();

        return column(
            row(
                spacer(2),
                text("model: ").dim().fit(),
                text(modelName).bold().fit(),
                spacer(3),
                text("cost: ").dim().fit(),
                text(String.format("$%.2f", state.cost())).green().fit(),
                spacer(3),
                text("context: ").dim().fit(),
                text(String.format("%.0f%%", pct)).fit(),
                spacer(3),
                text("mode: ").dim().fit(),
                text(state.permMode()).fit(),
                spacer()
            ),
            text("").length(1),
            row(spacer(2), text("Type a message or /help for commands").dim().fit(), spacer()),
            text("").length(1),
            text("").length(1)
        ).length(PANEL_HEIGHT);
    }
}
