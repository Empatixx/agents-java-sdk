package cz.krokviak.agents.cli.render.tui;

import dev.tamboui.toolkit.element.Element;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MarkdownRendererTest {

    @Test
    void plainTextPassthrough() {
        var spans = MarkdownRenderer.parseInline("hello world");
        assertEquals(1, spans.size());
    }

    @Test
    void boldParsed() {
        var spans = MarkdownRenderer.parseInline("this is **bold** text");
        assertEquals(3, spans.size()); // "this is " + "bold" + " text"
    }

    @Test
    void italicParsed() {
        var spans = MarkdownRenderer.parseInline("this is *italic* text");
        assertEquals(3, spans.size());
    }

    @Test
    void codeParsed() {
        var spans = MarkdownRenderer.parseInline("run `npm install` now");
        assertEquals(3, spans.size());
    }

    @Test
    void boldAndCodeMixed() {
        var spans = MarkdownRenderer.parseInline("**important**: use `git push`");
        assertTrue(spans.size() >= 3);
    }

    @Test
    void emptyStringReturnsEmpty() {
        var spans = MarkdownRenderer.parseInline("");
        assertTrue(spans.isEmpty());
    }

    @Test
    void nullReturnsEmpty() {
        var spans = MarkdownRenderer.parseInline(null);
        assertTrue(spans.isEmpty());
    }

    @Test
    void headerRendered() {
        var el = MarkdownRenderer.renderLine("# Title", "  ");
        assertNotNull(el);
    }

    @Test
    void h2Rendered() {
        var el = MarkdownRenderer.renderLine("## Subtitle", "  ");
        assertNotNull(el);
    }

    @Test
    void listBulletRendered() {
        var el = MarkdownRenderer.renderLine("- item one", "    ");
        assertNotNull(el);
    }

    @Test
    void numberedListRendered() {
        var el = MarkdownRenderer.renderLine("1. first step", "    ");
        assertNotNull(el);
    }

    @Test
    void plainLineWithPrefix() {
        var el = MarkdownRenderer.renderLine("just text", "    ");
        assertNotNull(el);
    }

    @Test
    void emptyLineRendered() {
        var el = MarkdownRenderer.renderLine("", "  ");
        assertNotNull(el);
    }

    @Test
    void multipleBoldsInOneLine() {
        var spans = MarkdownRenderer.parseInline("**a** and **b** and **c**");
        assertTrue(spans.size() >= 5); // a, " and ", b, " and ", c
    }

    @Test
    void nestedBoldItalic() {
        var spans = MarkdownRenderer.parseInline("***bold italic***");
        assertEquals(1, spans.size());
    }
}
