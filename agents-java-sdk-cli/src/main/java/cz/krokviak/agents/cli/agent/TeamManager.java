package cz.krokviak.agents.cli.agent;

import cz.krokviak.agents.cli.task.TaskManager;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

public class TeamManager {
    private final ConcurrentHashMap<String, Team> teams = new ConcurrentHashMap<>();

    public Team createTeam(String name) {
        Team team = new Team(name, new TaskManager());
        teams.put(name, team);
        return team;
    }

    public Team getTeam(String name) {
        return teams.get(name);
    }

    public Collection<Team> listTeams() {
        return teams.values();
    }

    public boolean deleteTeam(String name) {
        return teams.remove(name) != null;
    }
}
