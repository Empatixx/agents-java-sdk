package cz.krokviak.agents.tool;

public sealed interface Tool permits FunctionToolImpl, AgentTool, FileSearchTool, WebSearchTool, ComputerTool, MCPTool {
    String name();
    String description();
    default boolean isEnabled() { return true; }
}
