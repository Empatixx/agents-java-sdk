package cz.krokviak.agents.agent.spawn;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

public class AgentRegistry {
    private final ConcurrentHashMap<String, RunningAgent> agents = new ConcurrentHashMap<>();

    public void register(String name, RunningAgent agent) {
        agents.put(name, agent);
    }

    public RunningAgent get(String name) {
        return agents.get(name);
    }

    public Collection<RunningAgent> list() {
        return agents.values();
    }

    public RunningAgent remove(String name) {
        return agents.remove(name);
    }
}
