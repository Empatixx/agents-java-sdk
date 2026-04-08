package cz.krokviak.agents.cli.memory;

public record Memory(
    String name,
    String description,
    MemoryType type,
    String content,
    String filePath
) {}
