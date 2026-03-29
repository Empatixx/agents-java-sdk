package cz.krokviak.agents.hook;

import cz.krokviak.agents.context.RunContext;
import cz.krokviak.agents.model.ModelResponse;
import cz.krokviak.agents.model.LlmContext;
import cz.krokviak.agents.tool.Tool;
import cz.krokviak.agents.tool.ToolOutput;
import java.util.Map;

public interface RunHooks<T> {
    default void onAgentStart(RunContext<T> ctx, Object agent) {}
    default void onAgentEnd(RunContext<T> ctx, Object agent, Object output) {}
    default void onLlmStart(RunContext<T> ctx, LlmContext llmCtx) {}
    default void onLlmEnd(RunContext<T> ctx, LlmContext llmCtx, ModelResponse response) {}
    default void onToolStart(RunContext<T> ctx, Tool tool, Map<String, Object> args) {}
    default void onToolEnd(RunContext<T> ctx, Tool tool, ToolOutput result) {}
    default void onHandoff(RunContext<T> ctx, Object from, Object to) {}
}
