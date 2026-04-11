package cz.krokviak.agents.cli.paste;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PasteHandlerTest {

    @TempDir Path tempDir;

    @Test
    void shortTextIsNotPaste() {
        var handler = new PasteHandler(tempDir);
        assertFalse(handler.isPaste("hello world"));
    }

    @Test
    void longTextIsPaste() {
        var handler = new PasteHandler(tempDir);
        assertTrue(handler.isPaste("x".repeat(600)));
    }

    @Test
    void multiLineIsPaste() {
        var handler = new PasteHandler(tempDir);
        assertTrue(handler.isPaste("line1\nline2\nline3\nline4"));
    }

    @Test
    void nullIsNotPaste() {
        var handler = new PasteHandler(tempDir);
        assertFalse(handler.isPaste(null));
    }

    @Test
    void savePasteCreatesFile() {
        var handler = new PasteHandler(tempDir);
        String content = "x".repeat(600);
        var result = handler.savePaste(content);

        assertNotNull(result);
        assertTrue(java.nio.file.Files.isRegularFile(result.filePath()));
        assertEquals(content, result.content());
        assertEquals(1, result.lineCount());
        assertTrue(result.reference().contains("[Pasted text:"));
    }

    @Test
    void savePasteMultiLine() {
        var handler = new PasteHandler(tempDir);
        String content = "line1\nline2\nline3\nline4\nline5";
        var result = handler.savePaste(content);

        assertNotNull(result);
        assertEquals(5, result.lineCount());
    }

    @Test
    void sameContentSameHash() {
        var handler = new PasteHandler(tempDir);
        var r1 = handler.savePaste("x".repeat(600));
        var r2 = handler.savePaste("x".repeat(600));

        assertEquals(r1.filePath(), r2.filePath()); // same hash → same file
    }

    @Test
    void differentContentDifferentHash() {
        var handler = new PasteHandler(tempDir);
        var r1 = handler.savePaste("aaa".repeat(200));
        var r2 = handler.savePaste("bbb".repeat(200));

        assertNotEquals(r1.filePath(), r2.filePath());
    }
}
