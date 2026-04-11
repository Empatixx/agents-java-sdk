package cz.krokviak.agents.cli.memory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class MemoryLoader {
    private MemoryLoader() {}

    public static String loadProjectInstructions(Path cwd) {
        return loadProjectInstructions(cwd, null);
    }

    public static String loadProjectInstructions(Path cwd, MemoryStore memoryStore) {
        StringBuilder sb = new StringBuilder();

        // Global instructions
        Path globalPath = Path.of(System.getProperty("user.home"), ".krok", "AGENTS.md");
        appendIfExists(sb, globalPath, "## Global Instructions");

        // Project .krok/AGENTS.md
        Path projectDotKrokPath = cwd.resolve(".krok").resolve("AGENTS.md");
        appendIfExists(sb, projectDotKrokPath, "## Project Instructions (.krok/AGENTS.md)");

        // Project root AGENTS.md
        Path projectRootPath = cwd.resolve("AGENTS.md");
        appendIfExists(sb, projectRootPath, "## Project Instructions (AGENTS.md)");

        // Auto-memory from MemoryStore
        if (memoryStore != null) {
            String memoryPrompt = memoryStore.getMemoryPrompt();
            if (!memoryPrompt.isBlank()) {
                if (!sb.isEmpty()) sb.append("\n\n");
                sb.append("## Auto-Memory\n\n").append(memoryPrompt.strip());
            }
        }

        return sb.toString();
    }

    private static void appendIfExists(StringBuilder sb, Path path, String header) {
        if (Files.isRegularFile(path)) {
            try {
                String content = Files.readString(path);
                if (!content.isBlank()) {
                    if (!sb.isEmpty()) sb.append("\n\n");
                    sb.append(header).append("\n\n").append(content.strip());
                }
            } catch (IOException ignored) {}
        }
    }
}
