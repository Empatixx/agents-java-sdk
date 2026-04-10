package cz.krokviak.agents.cli.render.tui;

import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.elements.ListElement;

import static dev.tamboui.toolkit.Toolkit.*;

/** Scrollable output log. Pure view — reads from CliState. */
public final class OutputLogComponent {
    private OutputLogComponent() {}

    private static final ListElement<?> LOG = list()
        .stickyScroll()
        .scrollbar(dev.tamboui.widgets.common.ScrollBarPolicy.AS_NEEDED)
        .displayOnly();

    public static ListElement<?> instance() { return LOG; }

    public static Element render(CliState state) {
        return column(
            spacer(),  // pushes content to bottom when few lines
            LOG
        ).fill();
    }
}
