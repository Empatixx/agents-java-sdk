package cz.krokviak.agents.cli.paste;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;

/**
 * Handles large text pastes: saves to disk, returns reference.
 * Detects paste by multi-line or length > threshold.
 */
public final class PasteHandler {
    private static final Logger log = LoggerFactory.getLogger(PasteHandler.class);
    private static final int PASTE_THRESHOLD_CHARS = 500;
    private static final int PASTE_THRESHOLD_LINES = 3;

    private final Path cacheDir;

    public PasteHandler() {
        this.cacheDir = Path.of(System.getProperty("user.home"), ".krok", "paste-cache");
    }

    public PasteHandler(Path cacheDir) {
        this.cacheDir = cacheDir;
    }

    /**
     * Check if input looks like a paste (multi-line or very long).
     */
    public boolean isPaste(String input) {
        if (input == null) return false;
        if (input.length() > PASTE_THRESHOLD_CHARS) return true;
        return input.split("\n").length > PASTE_THRESHOLD_LINES;
    }

    /**
     * Save pasted text to disk and return a reference.
     * Returns null if save fails — caller should use raw input.
     */
    public PasteResult savePaste(String input) {
        try {
            Files.createDirectories(cacheDir);

            String hash = sha256(input).substring(0, 12);
            int lineCount = input.split("\n").length;
            Path file = cacheDir.resolve(hash + ".txt");
            Files.writeString(file, input);

            String reference = "[Pasted text: " + file.toAbsolutePath() + ", " + lineCount + " lines]";
            return new PasteResult(file, reference, input, lineCount);
        } catch (IOException e) {
            log.warn("Failed to save paste", e);
            return null;
        }
    }

    public record PasteResult(Path filePath, String reference, String content, int lineCount) {}

    private static String sha256(String input) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            var sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(input.hashCode());
        }
    }
}
