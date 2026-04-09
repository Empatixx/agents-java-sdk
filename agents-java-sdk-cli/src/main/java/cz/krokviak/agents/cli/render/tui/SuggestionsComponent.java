package cz.krokviak.agents.cli.render.tui;

import dev.tamboui.style.Color;
import dev.tamboui.toolkit.element.Element;

import java.util.List;

import static dev.tamboui.toolkit.Toolkit.*;

/**
 * Shows command suggestions when user types "/".
 * Renders as a compact list above the input area.
 */
public final class SuggestionsComponent {
    private SuggestionsComponent() {}

    public static Element render(List<CommandTrie.Match> suggestions) {
        if (suggestions.isEmpty()) {
            return text("").length(0);
        }

        int maxShow = Math.min(suggestions.size(), 5);
        var items = new Element[maxShow];
        for (int i = 0; i < maxShow; i++) {
            var match = suggestions.get(i);
            items[i] = row(
                text("  /" + match.command()).bold().cyan().fit(),
                text("  " + match.description()).dim().fit()
            );
        }

        int remaining = suggestions.size() - maxShow;
        if (remaining > 0) {
            var all = new Element[maxShow + 1];
            System.arraycopy(items, 0, all, 0, maxShow);
            all[maxShow] = text("  +" + remaining + " more...").dim();
            return column(all).length(maxShow + 1);
        }

        return column(items).length(maxShow);
    }
}
