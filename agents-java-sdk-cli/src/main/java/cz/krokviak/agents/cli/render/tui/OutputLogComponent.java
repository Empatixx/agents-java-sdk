package cz.krokviak.agents.cli.render.tui;

import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.elements.ListElement;
import dev.tamboui.widgets.common.ScrollBarPolicy;

import static dev.tamboui.toolkit.Toolkit.*;

/** Scrollable output log. Pure view. */
public final class OutputLogComponent {
    private OutputLogComponent() {}

    private static final ListElement<?> LOG = list()
        .stickyScroll()
        .scrollbar(ScrollBarPolicy.AS_NEEDED)
        .displayOnly();

    public static ListElement<?> instance() { return LOG; }

    public static Element render(CliState state) {
        return LOG.fill();
    }
}
