package cz.krokviak.agents.cli.memory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MemoryStore {

    private static final Pattern INDEX_ENTRY = Pattern.compile(
        "^- \\[([^]]+)\\]\\(([^)]+)\\)\\s*(?:—\\s*(.*))?$");
    private static final Pattern FRONTMATTER_FIELD = Pattern.compile("^(\\w+):\\s*(.*)$");

    private final Path memoryDir;

    public MemoryStore(Path memoryDir) {
        this.memoryDir = memoryDir;
    }

    private Path indexPath() {
        return memoryDir.resolve("MEMORY.md");
    }

    /** Reads MEMORY.md and parses index entries. */
    public List<Memory> loadIndex() {
        Path index = indexPath();
        if (!Files.isRegularFile(index)) return List.of();
        try {
            List<Memory> result = new ArrayList<>();
            for (String line : Files.readAllLines(index)) {
                Matcher m = INDEX_ENTRY.matcher(line.trim());
                if (m.matches()) {
                    String name = m.group(1);
                    String filename = m.group(2);
                    String desc = m.group(3) != null ? m.group(3).trim() : "";
                    result.add(new Memory(name, desc, MemoryType.USER, "", filename));
                }
            }
            return result;
        } catch (IOException e) {
            return List.of();
        }
    }

    /** Reads a specific memory file and parses frontmatter + content. */
    public Optional<Memory> loadMemory(String filename) {
        Path file = memoryDir.resolve(filename);
        if (!Files.isRegularFile(file)) return Optional.empty();
        try {
            String raw = Files.readString(file);
            return Optional.of(parseFrontmatter(raw, filename));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private Memory parseFrontmatter(String raw, String filename) {
        String name = filename;
        String description = "";
        MemoryType type = MemoryType.USER;
        String content = raw;

        if (raw.startsWith("---")) {
            int end = raw.indexOf("\n---", 3);
            if (end > 0) {
                String fm = raw.substring(3, end).trim();
                content = raw.substring(end + 4).trim();
                for (String line : fm.split("\n")) {
                    Matcher m = FRONTMATTER_FIELD.matcher(line.trim());
                    if (m.matches()) {
                        String key = m.group(1).toLowerCase(Locale.ROOT);
                        String val = m.group(2).trim();
                        switch (key) {
                            case "name" -> name = val;
                            case "description" -> description = val;
                            case "type" -> {
                                try { type = MemoryType.valueOf(val.toUpperCase(Locale.ROOT)); }
                                catch (IllegalArgumentException ignored) {}
                            }
                        }
                    }
                }
            }
        }
        return new Memory(name, description, type, content, filename);
    }

    /** Saves a memory (writes file + updates index). */
    public void saveMemory(Memory memory) throws IOException {
        Files.createDirectories(memoryDir);
        MemoryWriter.writeMemoryFile(memoryDir, memory);
        MemoryWriter.updateIndex(indexPath(), memory.name(), memory.filePath(), memory.description());
    }

    /** Removes a memory file and its index entry. */
    public void deleteMemory(String name) throws IOException {
        List<Memory> index = loadIndex();
        for (Memory m : index) {
            if (m.name().equals(name)) {
                Path file = memoryDir.resolve(m.filePath());
                Files.deleteIfExists(file);
                break;
            }
        }
        // Remove from index
        Path idx = indexPath();
        if (Files.isRegularFile(idx)) {
            Pattern linePattern = Pattern.compile("^- \\[" + Pattern.quote(name) + "\\]\\(.*\\).*$");
            List<String> lines = new ArrayList<>(Files.readAllLines(idx));
            lines.removeIf(l -> linePattern.matcher(l).matches());
            Files.writeString(idx, String.join("\n", lines) + (lines.isEmpty() ? "" : "\n"));
        }
    }

    /** Returns all Memory entries (stub content) from the index. */
    public List<Memory> listMemories() {
        return loadIndex();
    }

    /** Fuzzy match on name + description. */
    public List<Memory> searchMemories(String query) {
        if (query == null || query.isBlank()) return listMemories();
        String q = query.toLowerCase(Locale.ROOT);
        List<Memory> result = new ArrayList<>();
        for (Memory m : loadIndex()) {
            if (m.name().toLowerCase(Locale.ROOT).contains(q)
                || m.description().toLowerCase(Locale.ROOT).contains(q)) {
                // Load full content
                result.add(loadMemory(m.filePath()).orElse(m));
            }
        }
        return result;
    }

    /**
     * Returns first 200 lines of MEMORY.md for system prompt injection.
     */
    public String getMemoryPrompt() {
        Path index = indexPath();
        if (!Files.isRegularFile(index)) return "";
        try {
            List<String> lines = Files.readAllLines(index);
            int limit = Math.min(200, lines.size());
            return String.join("\n", lines.subList(0, limit));
        } catch (IOException e) {
            return "";
        }
    }
}
