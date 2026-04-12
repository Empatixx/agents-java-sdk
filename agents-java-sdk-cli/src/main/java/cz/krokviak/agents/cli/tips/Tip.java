package cz.krokviak.agents.cli.tips;

/**
 * A short user-facing tip shown during spinner ticks.
 *
 * @param id        Stable identifier used for cooldown tracking.
 * @param text      One-line text displayed to the user.
 * @param category  Optional category tag (e.g. "workflow", "keybinding") for grouping.
 */
public record Tip(String id, String text, String category) {}
