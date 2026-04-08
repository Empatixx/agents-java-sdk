package cz.krokviak.agents.cli.memory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class MemoryWriter {

    private MemoryWriter() {}

    public static void writeMemoryFile(Path dir, Memory memory) throws IOException {
        Files.createDirectories(dir);
        Path file = dir.resolve(memory.filePath());
        String content = """
            ---
            name: %s
            description: %s
            type: %s
            ---
            %s
            """.formatted(memory.name(), memory.description(), memory.type().name().toLowerCase(), memory.content());
        Files.writeString(file, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    public static void updateIndex(Path memoryMdPath, String name, String filename, String description) throws IOException {
        String entry = "- [" + name + "](" + filename + ") — " + description;
        Pattern linePattern = Pattern.compile("^- \\[" + Pattern.quote(name) + "\\]\\(.*\\).*$");

        if (Files.exists(memoryMdPath)) {
            List<String> lines = new ArrayList<>(Files.readAllLines(memoryMdPath));
            boolean replaced = false;
            for (int i = 0; i < lines.size(); i++) {
                if (linePattern.matcher(lines.get(i)).matches()) {
                    lines.set(i, entry);
                    replaced = true;
                    break;
                }
            }
            if (!replaced) {
                lines.add(entry);
            }
            Files.writeString(memoryMdPath, String.join("\n", lines) + "\n",
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } else {
            Files.createDirectories(memoryMdPath.getParent());
            Files.writeString(memoryMdPath, entry + "\n",
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        }
    }
}
