package cz.krokviak.agents.tool;

public sealed interface Tool permits FunctionToolImpl, AgentTool, FileSearchTool, WebSearchTool, ComputerTool {
    String name();
    String description();
}
