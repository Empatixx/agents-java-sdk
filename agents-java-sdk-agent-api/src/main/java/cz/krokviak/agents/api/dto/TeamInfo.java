package cz.krokviak.agents.api.dto;

import java.util.List;

public record TeamInfo(
    String name,
    List<String> taskIds
) {}
