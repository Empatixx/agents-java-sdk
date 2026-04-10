package cz.krokviak.agents.cli.render.tui;

import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.elements.ListElement;
import dev.tamboui.widgets.common.ScrollBarPolicy;

import static dev.tamboui.toolkit.Toolkit.*;

public final class OutputLogComponent {
    private OutputLogComponent() {}

    private static final ListElement<OutputLine> LOG = new ListElement<OutputLine>()
        .stickyScroll()
        .scrollbar(ScrollBarPolicy.AS_NEEDED)
        .displayOnly();

    public static Element render(CliController ctrl) {
        return LOG.data(ctrl.outputLines(), OutputLine::render).fill();
    }
}
