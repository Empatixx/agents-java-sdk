package cz.krokviak.agents.cli.paste;

import cz.krokviak.agents.api.event.AgentEvent;

import cz.krokviak.agents.cli.CliContext;
import cz.krokviak.agents.cli.event.CliEventBus;
import cz.krokviak.agents.cli.test.FakeRenderer;
import cz.krokviak.agents.runner.InputItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test: simulates what Repl.handlePasteAndImages does.
 */
class PasteImageIntegrationTest {

    @TempDir Path tempDir;
    @TempDir Path cacheDir;
    @TempDir Path pasteCache;

    CliContext ctx;
    PasteHandler pasteHandler;
    ImageHandler imageHandler;
    List<AgentEvent> events;
    int imageCounter;

    @BeforeEach
    void setup() {
        ctx = new CliContext(null, "test", null, null,
            new FakeRenderer(), null, null, tempDir,
            "", null, null, null, null);
        pasteHandler = new PasteHandler(pasteCache);
        imageHandler = new ImageHandler(cacheDir);
        events = new ArrayList<>();
        ctx.eventBus().subscribe(events::add);
        imageCounter = 0;
    }

    /** Simulates Repl.handlePasteAndImages logic */
    private String handlePasteAndImages(String input) {
        // 1. Image path
        if (imageHandler.isImagePath(input)) {
            try {
                var img = imageHandler.processImagePath(input);
                ctx.history().add(img);
                imageCounter++;
                ctx.eventBus().emit(new AgentEvent.ImageAttached(img.filePath(), imageCounter));
                return "[Image #" + imageCounter + "] attached. " + img.description();
            } catch (Exception e) {
                fail("Image processing failed: " + e.getMessage());
            }
        }

        // 2. Large paste
        if (pasteHandler.isPaste(input)) {
            var result = pasteHandler.savePaste(input);
            if (result != null) {
                ctx.history().add(new InputItem.SystemMessage(
                    "<pasted-content>\n" + result.content() + "\n</pasted-content>"));
                return result.reference();
            }
        }

        return input;
    }

    @Test
    void imagePathFlow() throws IOException {
        // Create test image
        var img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
        Path imgFile = tempDir.resolve("screenshot.png");
        ImageIO.write(img, "png", imgFile.toFile());

        // Simulate pasting the path
        String result = handlePasteAndImages(imgFile.toAbsolutePath().toString());

        // Should detect as image
        assertTrue(result.contains("[Image #1]"));
        assertTrue(result.contains("screenshot.png"));

        // History should contain ImageContent
        var imageItems = ctx.history().stream()
            .filter(i -> i instanceof InputItem.ImageContent)
            .toList();
        assertEquals(1, imageItems.size());

        var imageContent = (InputItem.ImageContent) imageItems.getFirst();
        assertEquals("image/png", imageContent.mediaType());
        assertNotNull(imageContent.base64Data());
        assertFalse(imageContent.base64Data().isEmpty());

        // Event should have fired
        var imageEvents = events.stream()
            .filter(e -> e instanceof AgentEvent.ImageAttached)
            .map(e -> (AgentEvent.ImageAttached) e)
            .toList();
        assertEquals(1, imageEvents.size());
        assertEquals(1, imageEvents.getFirst().index());
    }

    @Test
    void multipleImagesGetIncrementingIndex() throws IOException {
        Path img1 = createImage("one.png");
        Path img2 = createImage("two.png");

        handlePasteAndImages(img1.toAbsolutePath().toString());
        String result2 = handlePasteAndImages(img2.toAbsolutePath().toString());

        assertTrue(result2.contains("[Image #2]"));
        assertEquals(2, imageCounter);
    }

    @Test
    void largePasteFlow() {
        String longText = "x".repeat(600);

        String result = handlePasteAndImages(longText);

        assertTrue(result.contains("[Pasted text:"));
        // History should contain SystemMessage with pasted content
        var systemMsgs = ctx.history().stream()
            .filter(i -> i instanceof InputItem.SystemMessage)
            .map(i -> ((InputItem.SystemMessage) i).content())
            .toList();
        assertEquals(1, systemMsgs.size());
        assertTrue(systemMsgs.getFirst().contains("x".repeat(100)));
    }

    @Test
    void normalTextPassesThrough() {
        String input = "fix the bug in main.java";
        String result = handlePasteAndImages(input);
        assertEquals(input, result);
        assertTrue(ctx.history().isEmpty());
    }

    @Test
    void nonexistentImagePathPassesThrough() {
        String input = "/nonexistent/path/image.png";
        String result = handlePasteAndImages(input);
        assertEquals(input, result); // File doesn't exist → not detected as image
    }

    @Test
    void textFilePathNotDetectedAsImage() throws IOException {
        Path txt = tempDir.resolve("readme.txt");
        Files.writeString(txt, "hello");

        String result = handlePasteAndImages(txt.toAbsolutePath().toString());
        assertEquals(txt.toAbsolutePath().toString(), result); // .txt not image
    }

    @Test
    void imageBase64IsValidAndDecodable() throws IOException {
        Path img = createImage("valid.png");
        handlePasteAndImages(img.toAbsolutePath().toString());

        var imageContent = (InputItem.ImageContent) ctx.history().stream()
            .filter(i -> i instanceof InputItem.ImageContent)
            .findFirst().orElseThrow();

        byte[] decoded = java.util.Base64.getDecoder().decode(imageContent.base64Data());
        assertTrue(decoded.length > 0);

        // Should be valid PNG
        var readBack = ImageIO.read(new java.io.ByteArrayInputStream(decoded));
        assertNotNull(readBack);
        assertEquals(50, readBack.getWidth());
        assertEquals(50, readBack.getHeight());
    }

    private Path createImage(String name) throws IOException {
        var img = new BufferedImage(50, 50, BufferedImage.TYPE_INT_ARGB);
        Path file = tempDir.resolve(name);
        ImageIO.write(img, "png", file.toFile());
        return file;
    }
}
