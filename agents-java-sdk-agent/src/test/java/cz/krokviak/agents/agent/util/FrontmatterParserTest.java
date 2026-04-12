package cz.krokviak.agents.agent.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FrontmatterParserTest {

    @Test
    void fullFrontmatterParsed() {
        String md = """
            ---
            name: concise
            description: Brief, no preamble
            ---
            Body goes here.
            """;
        var p = FrontmatterParser.parse(md);
        assertEquals("concise", p.metadata().get("name"));
        assertEquals("Brief, no preamble", p.metadata().get("description"));
        assertTrue(p.body().startsWith("Body goes here"));
    }

    @Test
    void doubleQuotesStripped() {
        var p = FrontmatterParser.parse("---\nname: \"quoted value\"\n---\nbody");
        assertEquals("quoted value", p.metadata().get("name"));
    }

    @Test
    void singleQuotesStripped() {
        var p = FrontmatterParser.parse("---\nname: 'single'\n---\nbody");
        assertEquals("single", p.metadata().get("name"));
    }

    @Test
    void noFrontmatterTreatedAsBodyOnly() {
        var p = FrontmatterParser.parse("plain markdown");
        assertTrue(p.metadata().isEmpty());
        assertEquals("plain markdown", p.body());
    }

    @Test
    void unclosedFrontmatterTreatedAsBody() {
        var p = FrontmatterParser.parse("---\nname: missing closing marker");
        assertTrue(p.metadata().isEmpty());
        assertTrue(p.body().startsWith("---"));
    }

    @Test
    void blankReturnsNull() {
        assertNull(FrontmatterParser.parse(""));
        assertNull(FrontmatterParser.parse("   \n\n "));
        assertNull(FrontmatterParser.parse(null));
    }

    @Test
    void colonlessLinesIgnored() {
        var p = FrontmatterParser.parse("---\nname: x\nnot-a-pair-line\ndesc: y\n---\nbody");
        assertEquals(2, p.metadata().size());
        assertEquals("x", p.metadata().get("name"));
        assertEquals("y", p.metadata().get("desc"));
    }

    @Test
    void metadataOrderPreserved() {
        var p = FrontmatterParser.parse("---\nz: 1\na: 2\nm: 3\n---\n");
        assertEquals("[z, a, m]", p.metadata().keySet().toString());
    }
}
