package cz.krokviak.agents.cli.tool;

import cz.krokviak.agents.cli.skill.Skill;
import cz.krokviak.agents.cli.skill.SkillRegistry;
import cz.krokviak.agents.context.ToolContext;
import cz.krokviak.agents.tool.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * ExecutableTool that looks up a skill by name and returns its content
 * as system context for the next turn.
 */
public class SkillTool implements ExecutableTool {

    private final SkillRegistry registry;
    private final ToolDefinition toolDefinition;

    public SkillTool(SkillRegistry registry) {
        this.registry = registry;
        this.toolDefinition = new ToolDefinition(
            "skill",
            "Execute a skill — a reusable workflow defined in a markdown file. "
                + "Provide the skill name and optional arguments.",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "skill", Map.of("type", "string", "description", "Name of the skill to execute"),
                    "args", Map.of("type", "string", "description", "Optional arguments to pass to the skill")
                ),
                "required", List.of("skill")
            )
        );
    }

    @Override public String name() { return "skill"; }
    @Override public String description() { return toolDefinition.description(); }
    @Override public ToolDefinition definition() { return toolDefinition; }

    @Override
    public ToolOutput execute(ToolArgs args, ToolContext<?> ctx) {
        String skillName = args.get("skill", String.class);
        if (skillName == null || skillName.isBlank()) {
            return ToolOutput.text("Error: skill name is required.\n" + availableSkills());
        }

        Optional<Skill> found = registry.get(skillName);
        if (found.isEmpty()) {
            // Try fuzzy search
            var candidates = registry.search(skillName);
            if (candidates.isEmpty()) {
                return ToolOutput.text("Error: skill '" + skillName + "' not found.\n" + availableSkills());
            }
            // Use best match
            found = Optional.of(candidates.get(0));
        }

        Skill skill = found.get();
        String skillArgs = args.get("args", String.class);

        StringBuilder sb = new StringBuilder();
        sb.append("<skill name=\"").append(skill.name()).append("\">\n");
        sb.append(skill.content());
        if (skillArgs != null && !skillArgs.isBlank()) {
            sb.append("\n\n## Arguments\n").append(skillArgs);
        }
        sb.append("\n</skill>");

        return ToolOutput.text(sb.toString());
    }

    private String availableSkills() {
        List<Skill> all = registry.list();
        if (all.isEmpty()) return "No skills are currently loaded.";
        return "Available skills:\n" + all.stream()
            .map(s -> "  - " + s.name() + (s.description().isBlank() ? "" : ": " + s.description()))
            .collect(Collectors.joining("\n"));
    }
}
