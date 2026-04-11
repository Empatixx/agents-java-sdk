package cz.krokviak.agents.cli.plugin.marketplace;

/**
 * Where a marketplace comes from.
 *
 * <pre>
 * { "source": "github", "repo": "owner/repo", "ref": "main" }
 * { "source": "git", "url": "https://git.example.com/repo.git" }
 * { "source": "url", "url": "https://example.com/marketplace.json" }
 * { "source": "directory", "path": "/local/path" }
 * </pre>
 */
public sealed interface MarketplaceSource {

    record GitHub(String repo, String ref) implements MarketplaceSource {
        public GitHub { if (ref == null || ref.isBlank()) ref = "main"; }
        public String gitUrl() { return "https://github.com/" + repo + ".git"; }
    }

    record Git(String url, String ref) implements MarketplaceSource {
        public Git { if (ref == null || ref.isBlank()) ref = "main"; }
    }

    record Url(String url) implements MarketplaceSource {}

    record Directory(String path) implements MarketplaceSource {}

    @SuppressWarnings("unchecked")
    static MarketplaceSource fromMap(java.util.Map<String, Object> map) {
        String source = (String) map.get("source");
        return switch (source) {
            case "github" -> new GitHub((String) map.get("repo"), (String) map.get("ref"));
            case "git" -> new Git((String) map.get("url"), (String) map.get("ref"));
            case "url" -> new Url((String) map.get("url"));
            case "directory" -> new Directory((String) map.get("path"));
            default -> throw new IllegalArgumentException("Unknown marketplace source: " + source);
        };
    }

    default java.util.Map<String, Object> toMap() {
        return switch (this) {
            case GitHub g -> java.util.Map.of("source", "github", "repo", g.repo(), "ref", g.ref());
            case Git g -> java.util.Map.of("source", "git", "url", g.url(), "ref", g.ref());
            case Url u -> java.util.Map.of("source", "url", "url", u.url());
            case Directory d -> java.util.Map.of("source", "directory", "path", d.path());
        };
    }
}
