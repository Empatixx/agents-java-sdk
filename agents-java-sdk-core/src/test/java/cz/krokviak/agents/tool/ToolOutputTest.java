package cz.krokviak.agents.tool;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ToolOutputTest {
    @Test
    void textOutputHoldsContent() {
        ToolOutput output = ToolOutput.text("Hello world");
        assertInstanceOf(ToolOutput.Text.class, output);
        assertEquals("Hello world", ((ToolOutput.Text) output).content());
    }
    @Test
    void imageOutputHoldsDataAndMimeType() {
        byte[] data = new byte[]{1, 2, 3};
        ToolOutput output = ToolOutput.image(data, "image/png");
        assertInstanceOf(ToolOutput.Image.class, output);
        var img = (ToolOutput.Image) output;
        assertArrayEquals(data, img.data());
        assertEquals("image/png", img.mimeType());
    }
    @Test
    void fileOutputHoldsDataAndName() {
        byte[] data = new byte[]{4, 5, 6};
        ToolOutput output = ToolOutput.file(data, "report.pdf");
        assertInstanceOf(ToolOutput.File.class, output);
        var file = (ToolOutput.File) output;
        assertArrayEquals(data, file.data());
        assertEquals("report.pdf", file.name());
    }
    @Test
    void patternMatchingWorks() {
        ToolOutput output = ToolOutput.text("test");
        String result = switch (output) {
            case ToolOutput.Text t -> "text:" + t.content();
            case ToolOutput.Image i -> "image:" + i.mimeType();
            case ToolOutput.File f -> "file:" + f.name();
        };
        assertEquals("text:test", result);
    }
}
