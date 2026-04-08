package cz.krokviak.agents.cli.agent;

import cz.krokviak.agents.cli.render.AgentStatus;
import cz.krokviak.agents.cli.task.TaskManager;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class Team {
    private final String name;
    private final List<RunningAgent> agents = new CopyOnWriteArrayList<>();
    private final TaskManager sharedTaskManager;

    public Team(String name, TaskManager sharedTaskManager) {
        this.name = name;
        this.sharedTaskManager = sharedTaskManager;
    }

    public String name() { return name; }
    public List<RunningAgent> agents() { return List.copyOf(agents); }
    public TaskManager sharedTaskManager() { return sharedTaskManager; }

    public void addAgent(RunningAgent agent) {
        agents.add(agent);
    }

    public void removeAgent(String agentName) {
        agents.removeIf(a -> a.name().equals(agentName));
    }

    public boolean isComplete() {
        if (agents.isEmpty()) return true;
        return agents.stream().allMatch(a ->
            a.status() == AgentStatus.COMPLETED
            || a.status() == AgentStatus.FAILED
            || a.status() == AgentStatus.KILLED);
    }
}
