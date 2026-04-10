package cz.krokviak.agents.cli.render.tui;

import dev.tamboui.toolkit.element.Element;

import java.util.List;

import static dev.tamboui.toolkit.Toolkit.*;

public final class InfoPanelComponent {
    private InfoPanelComponent() {}

    private static final int HEIGHT = 5;

    public static Element render(CliController ctrl, List<CommandTrie.Match> suggestions) {
        if (!suggestions.isEmpty()) return renderSuggestions(suggestions);
        if (ctrl.hasActiveAgents()) return renderAgents(ctrl);
        if (ctrl.isPlanMode()) return renderPlanMode(ctrl);
        return renderStatus(ctrl);
    }

    private static Element renderPlanMode(CliController ctrl) {
        String slug = ctrl.planSlug() != null ? ctrl.planSlug() : "—";
        return column(
            row(spacer(2), text("📋 PLAN MODE").bold().yellow().fit(), text(" — read-only tools only").dim().fit(), spacer()),
            row(spacer(2), text("Plan: ").dim().fit(), text(slug).fit(), spacer()),
            text(""),
            row(spacer(2), text("Shift+Tab or /plan to exit · Ctrl+O to expand results").dim().fit(), spacer()),
            text("")
        ).length(HEIGHT);
    }

    private static Element renderSuggestions(List<CommandTrie.Match> suggestions) {
        var rows = new Element[HEIGHT];
        for (int i = 0; i < HEIGHT; i++) {
            if (i < suggestions.size()) {
                var m = suggestions.get(i);
                rows[i] = row(spacer(2), text("/" + m.command()).dim().bold().fit(),
                    spacer(2), text(m.description()).dim().fit(), spacer());
            } else {
                rows[i] = text("");
            }
        }
        return column(rows).length(HEIGHT);
    }

    private static Element renderAgents(CliController ctrl) {
        var rows = new Element[HEIGHT];
        int r = 0;
        for (var agent : ctrl.activeAgents()) {
            if (r >= HEIGHT) break;
            String detail = agent.detail().isEmpty() ? "running..." : agent.detail();
            rows[r++] = row(spacer(2), spinner().cyan().fit(),
                text(" " + agent.name()).bold().cyan().fit(),
                text("  " + detail).dim().fit(), spacer());
            if (r < HEIGHT && !agent.toolCalls().isEmpty()) {
                rows[r++] = row(spacer(4), text(agent.toolCalls().getLast()).dim().fit(), spacer());
            }
        }
        for (int i = r; i < HEIGHT; i++) rows[i] = text("");
        return column(rows).length(HEIGHT);
    }

    private static Element renderStatus(CliController ctrl) {
        double pct = ctrl.tokensMax() > 0 ? (double) ctrl.tokensUsed() / ctrl.tokensMax() * 100 : 0;
        String m = ctrl.model().isEmpty() ? "—" : ctrl.model();
        return column(
            row(spacer(2), text("model: ").dim().fit(), text(m).bold().fit(),
                spacer(3), text("cost: ").dim().fit(), text(String.format("$%.2f", ctrl.cost())).green().fit(),
                spacer(3), text("context: ").dim().fit(), text(String.format("%.0f%%", pct)).fit(),
                spacer(3), text("mode: ").dim().fit(), text(ctrl.permMode()).fit(), spacer()),
            text(""), row(spacer(2), text("Type a message or /help for commands").dim().fit(), spacer()),
            text(""), text("")
        ).length(HEIGHT);
    }
}
