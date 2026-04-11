package cz.krokviak.agents.cli.plugin.marketplace;

/**
 * A single plugin entry within a marketplace manifest.
 */
/**
 * A single plugin entry within a marketplace manifest.
 * source can be a local path ("./plugins/foo") or a remote reference.
 */
public record MarketplaceEntry(
    String id,
    String name,
    String version,
    String description,
    String source,
    String category,
    String author
) {
    public MarketplaceEntry {
        // Use name as id if id is blank
        if ((id == null || id.isBlank()) && name != null) id = name;
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Plugin entry requires id or name");
    }

    /** Whether the source is a local path within the marketplace repo. */
    public boolean isLocalSource() {
        return source != null && (source.startsWith("./") || source.startsWith("../"));
    }
}
