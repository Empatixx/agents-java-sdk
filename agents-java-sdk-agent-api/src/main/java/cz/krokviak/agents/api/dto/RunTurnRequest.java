package cz.krokviak.agents.api.dto;

import java.util.List;

public record RunTurnRequest(
    String userText,
    List<String> attachedImagePaths,
    Integer maxTurnsOverride
) {
    public static RunTurnRequest of(String text) {
        return new RunTurnRequest(text, List.of(), null);
    }
}
