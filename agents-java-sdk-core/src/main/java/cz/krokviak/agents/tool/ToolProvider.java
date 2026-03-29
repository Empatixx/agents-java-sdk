package cz.krokviak.agents.tool;

import java.util.List;

public interface ToolProvider extends AutoCloseable {
    List<Tool> provideTools() throws Exception;
    @Override
    default void close() {}
}
