package cz.krokviak.agents.cli.skill;

import java.util.*;
import java.util.stream.Collectors;

public class SkillRegistry {

    private final Map<String, Skill> skills = new LinkedHashMap<>();

    public void register(Skill skill) {
        skills.put(skill.name(), skill);
    }

    public Optional<Skill> get(String name) {
        return Optional.ofNullable(skills.get(name));
    }

    public List<Skill> list() {
        return List.copyOf(skills.values());
    }

    /**
     * Fuzzy name match: returns skills whose name contains the query (case-insensitive),
     * sorted by how early the match appears.
     */
    public List<Skill> search(String query) {
        if (query == null || query.isBlank()) return list();
        String q = query.toLowerCase();
        return skills.values().stream()
            .filter(s -> s.name().toLowerCase().contains(q)
                      || s.description().toLowerCase().contains(q))
            .sorted(Comparator.comparingInt(s -> {
                int idx = s.name().toLowerCase().indexOf(q);
                return idx >= 0 ? idx : Integer.MAX_VALUE;
            }))
            .collect(Collectors.toList());
    }

    public int size() {
        return skills.size();
    }
}
