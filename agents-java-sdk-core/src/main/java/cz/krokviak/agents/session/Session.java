package cz.krokviak.agents.session;

import cz.krokviak.agents.runner.InputItem;
import cz.krokviak.agents.runner.RunItem;
import java.util.List;

public interface Session {
    List<InputItem> getHistory(String sessionId);
    void save(String sessionId, List<RunItem> newItems);
}
