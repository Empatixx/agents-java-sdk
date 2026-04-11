package cz.krokviak.agents.cli.plugin.marketplace;

import java.util.List;

/**
 * Parsed marketplace.json — a registry of available plugins.
 *
 * <pre>
 * {
 *   "name": "my-marketplace",
 *   "description": "Official plugins",
 *   "version": "1.0.0",
 *   "plugins": [
 *     { "id": "plugin-a", "name": "Plugin A", "version": "1.0.0", "description": "...", "source": "./plugins/a" }
 *   ]
 * }
 * </pre>
 */
public record MarketplaceManifest(
    String name,
    String description,
    String version,
    List<MarketplaceEntry> plugins
) {
    public MarketplaceManifest {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Marketplace name required");
        if (plugins == null) plugins = List.of();
    }
}
