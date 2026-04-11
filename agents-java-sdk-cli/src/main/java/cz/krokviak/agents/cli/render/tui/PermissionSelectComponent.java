package cz.krokviak.agents.cli.render.tui;

import dev.tamboui.style.Color;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.event.EventResult;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

import static dev.tamboui.toolkit.Toolkit.*;

/**
 * Permission/question prompt with manual scrolling.
 * Shows max 15 items at a time, shifts viewport as selection moves.
 */
public final class PermissionSelectComponent {
    private PermissionSelectComponent() {}

    private static final int MAX_VISIBLE = 15;

    public static Element render(String header, String[] options,
                                 dev.tamboui.toolkit.elements.ListElement<?> optionListUnused,
                                 IntConsumer onSelect) {
        return render(header, options, optionListUnused, onSelect, null);
    }

    public static Element render(String header, String[] options,
                                 dev.tamboui.toolkit.elements.ListElement<?> optionListUnused,
                                 IntConsumer onSelect, CliController ctrl) {

        int totalItems = options.length;
        int visibleCount = Math.min(totalItems, MAX_VISIBLE);
        int scrollOffset = ctrl != null ? ctrl.permScrollOffset() : 0;

        // Build visible slice
        List<Element> rows = new ArrayList<>();
        rows.add(text(header).bold());
        rows.add(text(""));

        // Scroll indicator top
        if (scrollOffset > 0) {
            rows.add(text("    \u25b2 " + scrollOffset + " more above").dim());
        }

        for (int i = scrollOffset; i < Math.min(scrollOffset + visibleCount, totalItems); i++) {
            boolean selected = (i == optionListUnused.selected());
            String raw = options[i];
            // Support two-line items: "title\ndescription"
            int nl = raw.indexOf('\n');
            String title = nl >= 0 ? raw.substring(0, nl) : raw;
            String desc = nl >= 0 ? raw.substring(nl + 1) : null;

            String prefix = selected ? "  \u276f " : "    ";
            String num = (i + 1) + ". ";

            if (selected) {
                rows.add(text(prefix + num + title).yellow().bold());
            } else {
                rows.add(text(prefix + num + title));
            }
            if (desc != null && !desc.isBlank()) {
                rows.add(text("      " + desc).dim());
            }
        }

        // Scroll indicator bottom
        int remaining = totalItems - scrollOffset - visibleCount;
        if (remaining > 0) {
            rows.add(text("    \u25bc " + remaining + " more below").dim());
        }

        int height = rows.size() + 2; // +2 for border

        return row(
            spacer(1),
            panel(
                row(spacer(1), column(rows.toArray(Element[]::new)), spacer(1))
            ).rounded().borderColor(Color.YELLOW).fill()
                .focusable()
                .onKeyEvent(event -> {
                    int sel = optionListUnused.selected();
                    if (event.isConfirm()) {
                        onSelect.accept(sel);
                        return EventResult.HANDLED;
                    }
                    if (event.isUp()) {
                        int newSel = Math.max(0, sel - 1);
                        optionListUnused.selected(newSel);
                        if (ctrl != null && newSel < ctrl.permScrollOffset())
                            ctrl.setPermScrollOffset(newSel);
                        return EventResult.HANDLED;
                    }
                    if (event.isDown()) {
                        int newSel = Math.min(totalItems - 1, sel + 1);
                        optionListUnused.selected(newSel);
                        if (ctrl != null && newSel >= ctrl.permScrollOffset() + visibleCount)
                            ctrl.setPermScrollOffset(newSel - visibleCount + 1);
                        return EventResult.HANDLED;
                    }
                    for (int i = 0; i < totalItems && i < 9; i++) {
                        if (event.isChar((char) ('1' + i))) {
                            onSelect.accept(i);
                            return EventResult.HANDLED;
                        }
                    }
                    if (event.isChar('y')) { onSelect.accept(0); return EventResult.HANDLED; }
                    if (event.isChar('n')) { onSelect.accept(Math.min(2, totalItems - 1)); return EventResult.HANDLED; }
                    return EventResult.UNHANDLED;
                }),
            spacer(1)
        ).length(height);
    }
}
