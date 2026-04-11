package cz.krokviak.agents.cli.paste;

import cz.krokviak.agents.runner.InputItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;

/**
 * Handles image detection, caching, resizing, and conversion to InputItem.ImageContent.
 */
public final class ImageHandler {
    private static final Logger log = LoggerFactory.getLogger(ImageHandler.class);
    private static final Set<String> IMAGE_EXTENSIONS = Set.of(
        ".png", ".jpg", ".jpeg", ".gif", ".webp", ".bmp", ".svg");
    private static final long MAX_IMAGE_SIZE = 3_750_000; // 3.75MB
    private static final int MAX_DIMENSION = 2048;

    private final Path cacheDir;

    public ImageHandler(String sessionId) {
        this.cacheDir = Path.of(System.getProperty("user.home"), ".krok", "image-cache", sessionId);
    }

    public ImageHandler(Path cacheDir) {
        this.cacheDir = cacheDir;
    }

    /**
     * Check if text is a path to an image file.
     */
    public boolean isImagePath(String text) {
        if (text == null) return false;
        String trimmed = text.trim();
        // Remove quotes
        if (trimmed.startsWith("\"") && trimmed.endsWith("\""))
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        if (trimmed.startsWith("'") && trimmed.endsWith("'"))
            trimmed = trimmed.substring(1, trimmed.length() - 1);

        String lower = trimmed.toLowerCase();
        return IMAGE_EXTENSIONS.stream().anyMatch(lower::endsWith) && Files.isRegularFile(Path.of(trimmed));
    }

    /**
     * Process an image file path: cache it, resize if needed, return ImageContent.
     */
    public InputItem.ImageContent processImagePath(String imagePath) throws IOException {
        String trimmed = imagePath.trim().replaceAll("^[\"']|[\"']$", "");
        Path source = Path.of(trimmed);
        String ext = getExtension(source);
        String mediaType = extensionToMediaType(ext);

        Files.createDirectories(cacheDir);
        String id = UUID.randomUUID().toString().substring(0, 8);
        Path cached = cacheDir.resolve(id + ext);

        // Copy and potentially resize
        byte[] data = Files.readAllBytes(source);
        if (data.length > MAX_IMAGE_SIZE && !ext.equals(".svg")) {
            data = resizeImage(data, ext);
        }
        Files.write(cached, data);

        String base64 = Base64.getEncoder().encodeToString(data);
        return new InputItem.ImageContent(cached.toAbsolutePath().toString(), mediaType, base64,
            "Image: " + source.getFileName());
    }

    /**
     * Try to grab image from clipboard (Linux: xclip).
     * Returns null if no image in clipboard.
     */
    public InputItem.ImageContent tryClipboardImage() {
        try {
            // Check if clipboard has image
            var check = new ProcessBuilder("xclip", "-selection", "clipboard", "-t", "TARGETS", "-o")
                .redirectErrorStream(true).start();
            String targets = new String(check.getInputStream().readAllBytes()).trim();
            check.waitFor();

            if (!targets.contains("image/png") && !targets.contains("image/jpeg")) return null;

            String mimeType = targets.contains("image/png") ? "image/png" : "image/jpeg";
            String ext = mimeType.equals("image/png") ? ".png" : ".jpg";

            Files.createDirectories(cacheDir);
            String id = UUID.randomUUID().toString().substring(0, 8);
            Path cached = cacheDir.resolve(id + ext);

            var grab = new ProcessBuilder("xclip", "-selection", "clipboard", "-t", mimeType, "-o")
                .redirectErrorStream(false).start();
            byte[] data = grab.getInputStream().readAllBytes();
            grab.waitFor();

            if (data.length == 0) return null;
            if (data.length > MAX_IMAGE_SIZE) data = resizeImage(data, ext);

            Files.write(cached, data);
            String base64 = Base64.getEncoder().encodeToString(data);
            return new InputItem.ImageContent(cached.toAbsolutePath().toString(), mimeType, base64,
                "Image from clipboard");
        } catch (Exception e) {
            log.debug("No clipboard image available: {}", e.getMessage());
            return null;
        }
    }

    private byte[] resizeImage(byte[] data, String ext) throws IOException {
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(data));
        if (img == null) return data;

        int w = img.getWidth();
        int h = img.getHeight();

        // Scale down to MAX_DIMENSION on longest side
        double scale = Math.min((double) MAX_DIMENSION / w, (double) MAX_DIMENSION / h);
        if (scale >= 1.0) return data; // already small enough

        int newW = (int) (w * scale);
        int newH = (int) (h * scale);

        var resized = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_ARGB);
        var g = resized.createGraphics();
        g.drawImage(img.getScaledInstance(newW, newH, java.awt.Image.SCALE_SMOOTH), 0, 0, null);
        g.dispose();

        var out = new ByteArrayOutputStream();
        ImageIO.write(resized, ext.replace(".", "").equals("jpg") ? "jpeg" : "png", out);
        return out.toByteArray();
    }

    private static String getExtension(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot).toLowerCase() : ".png";
    }

    private static String extensionToMediaType(String ext) {
        return switch (ext) {
            case ".png" -> "image/png";
            case ".jpg", ".jpeg" -> "image/jpeg";
            case ".gif" -> "image/gif";
            case ".webp" -> "image/webp";
            case ".bmp" -> "image/bmp";
            case ".svg" -> "image/svg+xml";
            default -> "image/png";
        };
    }
}
