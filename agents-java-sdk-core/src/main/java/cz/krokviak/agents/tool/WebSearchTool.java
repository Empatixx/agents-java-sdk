package cz.krokviak.agents.tool;

public final class WebSearchTool implements Tool {
    private final String searchContextSize;

    public WebSearchTool() {
        this("medium");
    }

    public WebSearchTool(String searchContextSize) {
        this.searchContextSize = searchContextSize;
    }

    @Override public String name() { return "web_search"; }
    @Override public String description() { return "Search the web"; }
    public String searchContextSize() { return searchContextSize; }
}
