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
        if (state.hasActiveAgents()) {
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
        int row = 0;

        // Show each active agent: header + last tool call
        for (var agent : state.activeAgents()) {
            if (row >= PANEL_HEIGHT) break;
            String detail = agent.detail().isEmpty() ? "running..." : agent.detail();
            rows[row++] = row(
                spacer(2),
                spinner().cyan().fit(),
                text(" " + agent.name()).bold().cyan().fit(),
                text("  " + detail).dim().fit(),
                spacer()
            );
            // Show last tool call for this agent
            if (row < PANEL_HEIGHT && !agent.toolCalls().isEmpty()) {
                String lastCall = agent.toolCalls().getLast();
                rows[row++] = row(spacer(4), text(lastCall).dim().fit(), spacer());
            }
        }

        // Fill remaining with empty
        for (int i = row; i < PANEL_HEIGHT; i++) {
            rows[i] = text("");
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
