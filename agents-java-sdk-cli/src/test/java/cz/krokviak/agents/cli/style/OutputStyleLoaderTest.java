package cz.krokviak.agents.cli.style;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OutputStyleLoaderTest {

    @Test
    void parseFrontmatterAndBody() {
        String md = """
            ---
            name: concise
            description: Brief, no preamble
            ---
            Be terse. Output only the code.
            """;
        OutputStyle s = OutputStyleLoader.parse(md, "/tmp/concise.md");
        assertEquals("concise", s.name());
        assertEquals("Brief, no preamble", s.description());
        assertTrue(s.systemPrompt().startsWith("Be terse"));
    }

    @Test
    void nameFallsBackToFilename() {
        String md = "Body only, no frontmatter.";
        OutputStyle s = OutputStyleLoader.parse(md, "/tmp/learning.md");
        assertEquals("learning", s.name());
        assertEquals("", s.description());
        assertTrue(s.systemPrompt().contains("Body only"));
    }

    @Test
    void scansDirectoryAndIgnoresNonMarkdown(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("a.md"), "---\nname: a\n---\nA body");
        Files.writeString(dir.resolve("b.md"), "---\nname: b\n---\nB body");
        Files.writeString(dir.resolve("c.txt"), "ignored");
        List<OutputStyle> styles = OutputStyleLoader.scanDirectory(dir);
        assertEquals(2, styles.size());
        assertTrue(styles.stream().anyMatch(s -> s.name().equals("a")));
        assertTrue(styles.stream().anyMatch(s -> s.name().equals("b")));
    }

    @Test
    void blankContentReturnsNull() {
        assertNull(OutputStyleLoader.parse("", "/tmp/x.md"));
        assertNull(OutputStyleLoader.parse(null, "/tmp/x.md"));
    }
}
