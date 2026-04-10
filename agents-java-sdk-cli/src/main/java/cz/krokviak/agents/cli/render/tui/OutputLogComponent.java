package cz.krokviak.agents.cli.render.tui;

import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.elements.ListElement;
import dev.tamboui.widgets.common.ScrollBarPolicy;

import static dev.tamboui.toolkit.Toolkit.*;

/** Scrollable output log backed by reactive data binding. */
public final class OutputLogComponent {
    private OutputLogComponent() {}

    private static final ListElement<OutputLine> LOG = new ListElement<OutputLine>()
        .stickyScroll()
        .scrollbar(ScrollBarPolicy.AS_NEEDED)
        .displayOnly();

    public static ListElement<OutputLine> instance() { return LOG; }

    public static Element render(CliState state) {
        // Reactive: list rebuilds from outputLines each render cycle
        return LOG
            .data(state.outputLines(), OutputLine::render)
            .fill();
    }
}
