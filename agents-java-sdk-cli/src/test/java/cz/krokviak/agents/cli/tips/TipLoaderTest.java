package cz.krokviak.agents.cli.tips;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TipLoaderTest {

    @Test
    void parseFrontmatterTip() {
        String md = """
            ---
            id: compact
            category: workflow
            ---
            Running low on context? /compact summarises history.
            """;
        Tip t = TipLoader.parse(md, "/tmp/compact.md");
        assertEquals("compact", t.id());
        assertEquals("workflow", t.category());
        assertTrue(t.text().startsWith("Running low"));
    }

    @Test
    void idFallsBackToFilename() {
        String md = "Body only, no frontmatter.";
        Tip t = TipLoader.parse(md, "/tmp/plan.md");
        assertEquals("plan", t.id());
        assertEquals("", t.category());
    }

    @Test
    void blankOrEmptyReturnsNull() {
        assertNull(TipLoader.parse("", "/tmp/x.md"));
        assertNull(TipLoader.parse(null, "/tmp/x.md"));
        assertNull(TipLoader.parse("---\nid: blank\n---\n   ", "/tmp/x.md"));
    }

    @Test
    void builtinsHaveStableIds() {
        var builtins = TipLoader.builtin();
        assertFalse(builtins.isEmpty());
        assertTrue(builtins.stream().allMatch(t -> t.id() != null && !t.id().isBlank()));
        assertTrue(builtins.stream().allMatch(t -> t.text() != null && !t.text().isBlank()));
    }
}
