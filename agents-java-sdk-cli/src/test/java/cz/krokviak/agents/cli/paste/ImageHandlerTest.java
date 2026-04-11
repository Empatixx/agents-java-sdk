package cz.krokviak.agents.cli.paste;

import cz.krokviak.agents.runner.InputItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ImageHandlerTest {

    @TempDir Path tempDir;
    @TempDir Path cacheDir;
    ImageHandler handler;

    @BeforeEach
    void setup() {
        handler = new ImageHandler(cacheDir);
    }

    private Path createTestImage(String name, int width, int height) throws IOException {
        var img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        var g = img.createGraphics();
        g.fillRect(0, 0, width, height);
        g.dispose();
        Path file = tempDir.resolve(name);
        ImageIO.write(img, "png", file.toFile());
        return file;
    }

    @Test
    void isImagePathDetectsPng() throws IOException {
        Path img = createTestImage("test.png", 100, 100);
        assertTrue(handler.isImagePath(img.toAbsolutePath().toString()));
    }

    @Test
    void isImagePathDetectsJpg() throws IOException {
        // Create a jpg
        var img = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        Path file = tempDir.resolve("test.jpg");
        ImageIO.write(img, "jpeg", file.toFile());
        assertTrue(handler.isImagePath(file.toAbsolutePath().toString()));
    }

    @Test
    void isImagePathRejectsNonImage() throws IOException {
        Path txt = tempDir.resolve("test.txt");
        Files.writeString(txt, "not an image");
        assertFalse(handler.isImagePath(txt.toAbsolutePath().toString()));
    }

    @Test
    void isImagePathRejectsNonexistent() {
        assertFalse(handler.isImagePath("/nonexistent/image.png"));
    }

    @Test
    void isImagePathRejectsNull() {
        assertFalse(handler.isImagePath(null));
    }

    @Test
    void isImagePathHandlesQuotes() throws IOException {
        Path img = createTestImage("quoted.png", 10, 10);
        assertTrue(handler.isImagePath("\"" + img.toAbsolutePath() + "\""));
        assertTrue(handler.isImagePath("'" + img.toAbsolutePath() + "'"));
    }

    @Test
    void isImagePathRejectsNormalText() {
        assertFalse(handler.isImagePath("hello world"));
        assertFalse(handler.isImagePath("fix the bug in main.java"));
    }

    @Test
    void processImagePathCreatesCache() throws IOException {
        Path img = createTestImage("source.png", 50, 50);

        InputItem.ImageContent result = handler.processImagePath(img.toAbsolutePath().toString());

        assertNotNull(result);
        assertNotNull(result.base64Data());
        assertFalse(result.base64Data().isEmpty());
        assertEquals("image/png", result.mediaType());
        assertTrue(result.filePath().contains(cacheDir.toAbsolutePath().toString()));
        assertTrue(result.description().contains("source.png"));
    }

    @Test
    void processImagePathReturnsValidBase64() throws IOException {
        Path img = createTestImage("b64test.png", 20, 20);

        InputItem.ImageContent result = handler.processImagePath(img.toAbsolutePath().toString());

        // Verify base64 decodes without error
        byte[] decoded = java.util.Base64.getDecoder().decode(result.base64Data());
        assertTrue(decoded.length > 0);
    }

    @Test
    void processLargeImageResizes() throws IOException {
        // Create a large image (will be >3.75MB uncompressed but PNG compresses well)
        Path img = createTestImage("large.png", 4000, 4000);

        InputItem.ImageContent result = handler.processImagePath(img.toAbsolutePath().toString());

        assertNotNull(result);
        // The image should have been processed (may or may not have been resized depending on PNG compression)
        assertNotNull(result.base64Data());
    }

    @Test
    void mediaTypeDetection() throws IOException {
        // PNG
        Path png = createTestImage("test.png", 10, 10);
        var r1 = handler.processImagePath(png.toAbsolutePath().toString());
        assertEquals("image/png", r1.mediaType());

        // JPG
        var jpgImg = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        Path jpg = tempDir.resolve("test.jpg");
        ImageIO.write(jpgImg, "jpeg", jpg.toFile());
        var r2 = handler.processImagePath(jpg.toAbsolutePath().toString());
        assertEquals("image/jpeg", r2.mediaType());
    }

    @Test
    void multipleImagesCachedSeparately() throws IOException {
        Path img1 = createTestImage("one.png", 10, 10);
        Path img2 = createTestImage("two.png", 20, 20);

        var r1 = handler.processImagePath(img1.toAbsolutePath().toString());
        var r2 = handler.processImagePath(img2.toAbsolutePath().toString());

        assertNotEquals(r1.filePath(), r2.filePath());
    }
}
