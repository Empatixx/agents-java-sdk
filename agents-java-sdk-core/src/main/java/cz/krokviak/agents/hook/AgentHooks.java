package cz.krokviak.agents.hook;

import cz.krokviak.agents.context.RunContext;
import cz.krokviak.agents.tool.Tool;
import cz.krokviak.agents.tool.ToolOutput;
import java.util.Map;

public interface AgentHooks<T> {
    default void onStart(RunContext<T> ctx, Object agent) {}
    default void onEnd(RunContext<T> ctx, Object agent, Object output) {}
    default void onToolStart(RunContext<T> ctx, Tool tool, Map<String, Object> args) {}
    default void onToolEnd(RunContext<T> ctx, Tool tool, ToolOutput result) {}
}
