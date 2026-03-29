package cz.krokviak.agents.tool;

import java.util.List;

public final class FileSearchTool implements Tool {
    private final List<String> vectorStoreIds;

    public FileSearchTool(List<String> vectorStoreIds) {
        this.vectorStoreIds = List.copyOf(vectorStoreIds);
    }

    @Override public String name() { return "file_search"; }
    @Override public String description() { return "Search files in vector stores"; }
    public List<String> vectorStoreIds() { return vectorStoreIds; }
}
