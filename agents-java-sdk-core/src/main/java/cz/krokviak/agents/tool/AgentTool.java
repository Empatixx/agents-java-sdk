package cz.krokviak.agents.tool;

public final class AgentTool implements Tool {
    private final String name;
    private final String description;
    private final String agentName;

    public AgentTool(String name, String description, String agentName) {
        this.name = name;
        this.description = description;
        this.agentName = agentName;
    }

    @Override public String name() { return name; }
    @Override public String description() { return description; }
    public String agentName() { return agentName; }
}
