package cz.krokviak.agents.cli.render.tui;

import dev.tamboui.toolkit.element.Element;

import java.util.List;

import static dev.tamboui.toolkit.Toolkit.*;

/**
 * Command suggestions below the input area.
 * Two columns: left = command name, right = description. All dim/gray.
 */
public final class SuggestionsComponent {
    private SuggestionsComponent() {}

    public static Element render(List<CommandTrie.Match> suggestions) {
        if (suggestions.isEmpty()) {
            return text("").length(0);
        }

        int maxShow = Math.min(suggestions.size(), 5);
        var rows = new Element[maxShow];
        for (int i = 0; i < maxShow; i++) {
            var match = suggestions.get(i);
            rows[i] = row(
                spacer(2),
                text("/" + match.command()).dim().bold().fit(),
                spacer(2),
                text(match.description()).dim().fit(),
                spacer()
            );
        }

        return column(rows).length(maxShow);
    }
}
