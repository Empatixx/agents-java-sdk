package cz.krokviak.agents.cli.plugin.marketplace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/**
 * Manages marketplace registrations, plugin installation, and versioned cache.
 *
 * Directory layout:
 * <pre>
 * ~/.krok/plugins/
 * ├── known_marketplaces.json          # Registered marketplaces
 * ├── installed_plugins.json           # Installed plugin metadata
 * ├── marketplaces/                    # Cloned marketplace repos
 * │   └── my-marketplace/
 * │       └── marketplace.json
 * ├── cache/                           # Versioned plugin cache
 * │   └── plugin-name/
 * │       └── 1.0.0/
 * │           ├── plugin.json
 * │           ├── commands/
 * │           └── skills/
 * └── (local plugins also live here)
 * </pre>
 */
public class MarketplaceManager {
    private static final Logger log = LoggerFactory.getLogger(MarketplaceManager.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path pluginsRoot;
    private final Path marketplacesDir;
    private final Path cacheDir;
    private final Path knownFile;
    private final Path installedFile;

    public MarketplaceManager(Path pluginsRoot) {
        this.pluginsRoot = pluginsRoot;
        this.marketplacesDir = pluginsRoot.resolve("marketplaces");
        this.cacheDir = pluginsRoot.resolve("cache");
        this.knownFile = pluginsRoot.resolve("known_marketplaces.json");
        this.installedFile = pluginsRoot.resolve("installed_plugins.json");
    }

    public MarketplaceManager() {
        this(Path.of(System.getProperty("user.home"), ".krok", "plugins"));
    }

    // ========================= Marketplace Registration =========================

    /**
     * Add a marketplace source.
     * Accepts: "github:owner/repo", "git:url", "url:https://...", "/local/path"
     */
    public String addMarketplace(String input) throws IOException {
        MarketplaceSource source = parseSource(input);
        String name = inferName(input, source);

        // Clone/fetch
        Path mktDir = materializeMarketplace(name, source);

        // Validate marketplace.json exists
        Path manifestPath = findManifest(mktDir);
        if (manifestPath == null) {
            throw new IOException("No marketplace.json found in " + mktDir);
        }

        // Save to known_marketplaces.json
        Map<String, Object> known = loadKnown();
        known.put(name, Map.of(
            "source", source.toMap(),
            "installLocation", mktDir.toAbsolutePath().toString(),
            "lastUpdated", Instant.now().toString()
        ));
        saveKnown(known);

        return name;
    }

    /**
     * List registered marketplaces.
     */
    public Map<String, Object> listMarketplaces() {
        return loadKnown();
    }

    /**
     * Remove a marketplace.
     */
    public void removeMarketplace(String name) throws IOException {
        Map<String, Object> known = loadKnown();
        known.remove(name);
        saveKnown(known);

        Path mktDir = marketplacesDir.resolve(sanitize(name));
        if (Files.isDirectory(mktDir)) {
            deleteRecursive(mktDir);
        }
    }

    // ========================= Plugin Browsing =========================

    /**
     * List all plugins available across all marketplaces.
     */
    @SuppressWarnings("unchecked")
    public List<AvailablePlugin> browsePlugins() {
        List<AvailablePlugin> result = new ArrayList<>();
        Map<String, Object> known = loadKnown();

        for (var entry : known.entrySet()) {
            String mktName = entry.getKey();
            if (!(entry.getValue() instanceof Map<?, ?> mktData)) continue;
            String location = (String) ((Map<String, Object>) mktData).get("installLocation");
            if (location == null) continue;

            try {
                MarketplaceManifest manifest = loadManifest(Path.of(location));
                for (var plugin : manifest.plugins()) {
                    result.add(new AvailablePlugin(
                        plugin.id(), plugin.name(), plugin.version(),
                        plugin.description(), plugin.source(), plugin.category(), mktName));
                }
            } catch (Exception e) {
                log.warn( "Failed to load marketplace " + mktName, e);
            }
        }

        return result;
    }

    public record AvailablePlugin(String id, String name, String version,
                                   String description, String source, String category,
                                   String marketplace) {
        public String qualifiedId() { return id + "@" + marketplace; }
        public boolean isLocal() { return source != null && (source.startsWith("./") || source.startsWith("../")); }
    }

    // ========================= Plugin Install/Remove =========================

    /**
     * Install a plugin by ID (optionally qualified: "plugin-id@marketplace").
     */
    public InstalledPlugin installPlugin(String pluginId) throws IOException {
        // Find in marketplaces
        String marketplace = null;
        String bareId = pluginId;
        if (pluginId.contains("@")) {
            String[] parts = pluginId.split("@", 2);
            bareId = parts[0];
            marketplace = parts[1];
        }

        AvailablePlugin found = null;
        for (var avail : browsePlugins()) {
            if (avail.id().equals(bareId)) {
                if (marketplace == null || marketplace.equals(avail.marketplace())) {
                    found = avail;
                    break;
                }
            }
        }

        if (found == null) {
            throw new IOException("Plugin not found: " + pluginId);
        }

        String version = found.version() != null ? found.version() : "latest";
        Path cacheTarget = cacheDir.resolve(sanitize(bareId)).resolve(version);

        if (found.isLocal()) {
            // Local source — copy from marketplace repo
            Map<String, Object> known = loadKnown();
            @SuppressWarnings("unchecked")
            Map<String, Object> mktData = (Map<String, Object>) known.get(found.marketplace());
            String mktLocation = (String) mktData.get("installLocation");
            Path pluginSource = Path.of(mktLocation).resolve(found.source());

            if (!Files.isDirectory(pluginSource)) {
                throw new IOException("Plugin source not found: " + pluginSource);
            }
            if (Files.isDirectory(cacheTarget)) deleteRecursive(cacheTarget);
            copyRecursive(pluginSource, cacheTarget);
        } else if (found.source() != null && found.source().startsWith("git:")) {
            // Remote git source — clone
            String gitUrl = found.source().substring(4);
            if (Files.isDirectory(cacheTarget)) deleteRecursive(cacheTarget);
            GitOps.clone(gitUrl, null, cacheTarget);
            version = GitOps.headSha(cacheTarget);
        } else {
            throw new IOException("Unsupported plugin source: " + found.source());
        }

        // Track in installed_plugins.json
        Map<String, Object> installed = loadInstalled();
        installed.put(bareId, Map.of(
            "version", version,
            "marketplace", found.marketplace(),
            "installPath", cacheTarget.toAbsolutePath().toString(),
            "installedAt", Instant.now().toString(),
            "enabled", true
        ));
        saveInstalled(installed);

        return new InstalledPlugin(bareId, version, found.marketplace(),
            cacheTarget.toAbsolutePath().toString(), true);
    }

    /**
     * Install a plugin directly from a git URL (no marketplace needed).
     */
    public InstalledPlugin installFromGit(String gitInput) throws IOException {
        MarketplaceSource source = parseSource(gitInput);
        String name = inferName(gitInput, source);

        String gitUrl = switch (source) {
            case MarketplaceSource.GitHub g -> g.gitUrl();
            case MarketplaceSource.Git g -> g.url();
            default -> throw new IOException("Not a git source: " + gitInput);
        };
        String ref = switch (source) {
            case MarketplaceSource.GitHub g -> g.ref();
            case MarketplaceSource.Git g -> g.ref();
            default -> "main";
        };

        Path target = cacheDir.resolve(sanitize(name)).resolve("git");
        GitOps.cloneOrPull(gitUrl, ref, target);

        String sha = GitOps.headSha(target);

        Map<String, Object> installed = loadInstalled();
        installed.put(name, Map.of(
            "version", sha,
            "marketplace", "git",
            "installPath", target.toAbsolutePath().toString(),
            "installedAt", Instant.now().toString(),
            "enabled", true
        ));
        saveInstalled(installed);

        return new InstalledPlugin(name, sha, "git", target.toAbsolutePath().toString(), true);
    }

    /**
     * Remove an installed plugin.
     */
    public void removePlugin(String pluginId) throws IOException {
        Map<String, Object> installed = loadInstalled();
        @SuppressWarnings("unchecked")
        Map<String, Object> info = (Map<String, Object>) installed.get(pluginId);
        if (info != null) {
            String path = (String) info.get("installPath");
            if (path != null && Files.isDirectory(Path.of(path))) {
                deleteRecursive(Path.of(path));
            }
        }
        installed.remove(pluginId);
        saveInstalled(installed);
    }

    /**
     * Enable or disable an installed plugin.
     */
    @SuppressWarnings("unchecked")
    public void setEnabled(String pluginId, boolean enabled) throws IOException {
        Map<String, Object> installed = loadInstalled();
        Map<String, Object> info = (Map<String, Object>) installed.get(pluginId);
        if (info == null) throw new IOException("Plugin not installed: " + pluginId);
        Map<String, Object> updated = new HashMap<>(info);
        updated.put("enabled", enabled);
        installed.put(pluginId, updated);
        saveInstalled(installed);
    }

    /**
     * List installed plugins.
     */
    @SuppressWarnings("unchecked")
    public List<InstalledPlugin> listInstalled() {
        List<InstalledPlugin> result = new ArrayList<>();
        Map<String, Object> installed = loadInstalled();
        for (var entry : installed.entrySet()) {
            if (entry.getValue() instanceof Map<?, ?> info) {
                var m = (Map<String, Object>) info;
                result.add(new InstalledPlugin(
                    entry.getKey(),
                    (String) m.getOrDefault("version", "?"),
                    (String) m.getOrDefault("marketplace", "?"),
                    (String) m.get("installPath"),
                    Boolean.TRUE.equals(m.get("enabled"))
                ));
            }
        }
        return result;
    }

    /**
     * Get paths of all enabled installed plugins (for loading).
     */
    public List<Path> enabledPluginPaths() {
        List<Path> paths = new ArrayList<>();
        for (var p : listInstalled()) {
            if (p.enabled() && p.installPath() != null) {
                Path path = Path.of(p.installPath());
                if (Files.isDirectory(path)) paths.add(path);
            }
        }
        return paths;
    }

    public record InstalledPlugin(String id, String version, String marketplace,
                                   String installPath, boolean enabled) {}

    // ========================= Reconciliation =========================

    /**
     * Reconcile: for each known marketplace, ensure it's cloned/up-to-date.
     */
    @SuppressWarnings("unchecked")
    public void reconcile() {
        Map<String, Object> known = loadKnown();
        for (var entry : known.entrySet()) {
            String name = entry.getKey();
            if (!(entry.getValue() instanceof Map<?, ?> data)) continue;
            var mktData = (Map<String, Object>) data;

            try {
                Object sourceObj = mktData.get("source");
                if (!(sourceObj instanceof Map<?, ?> sourceMap)) continue;
                MarketplaceSource source = MarketplaceSource.fromMap((Map<String, Object>) sourceMap);
                materializeMarketplace(name, source);

                // Update lastUpdated
                mktData = new HashMap<>(mktData);
                mktData.put("lastUpdated", Instant.now().toString());
                known.put(name, mktData);
            } catch (Exception e) {
                log.warn( "Failed to reconcile marketplace " + name, e);
            }
        }
        saveKnown(known);
    }

    // ========================= Internal =========================

    private Path materializeMarketplace(String name, MarketplaceSource source) throws IOException {
        Path mktDir = marketplacesDir.resolve(sanitize(name));
        switch (source) {
            case MarketplaceSource.GitHub g -> GitOps.cloneOrPull(g.gitUrl(), g.ref(), mktDir);
            case MarketplaceSource.Git g -> GitOps.cloneOrPull(g.url(), g.ref(), mktDir);
            case MarketplaceSource.Directory d -> {
                if (!Files.isDirectory(Path.of(d.path()))) {
                    throw new IOException("Directory not found: " + d.path());
                }
                if (!Files.isDirectory(mktDir)) {
                    Files.createDirectories(mktDir.getParent());
                    Files.createSymbolicLink(mktDir, Path.of(d.path()));
                }
            }
            case MarketplaceSource.Url u ->
                throw new IOException("URL marketplace sources not yet supported");
        }
        return mktDir;
    }

    private Path findManifest(Path dir) {
        // Look for marketplace.json or .claude-plugin/marketplace.json (compat)
        Path direct = dir.resolve("marketplace.json");
        if (Files.isRegularFile(direct)) return direct;
        Path nested = dir.resolve(".claude-plugin").resolve("marketplace.json");
        if (Files.isRegularFile(nested)) return nested;
        // Also check .krok-plugin/marketplace.json
        Path krokNested = dir.resolve(".krok-plugin").resolve("marketplace.json");
        if (Files.isRegularFile(krokNested)) return krokNested;
        return null;
    }

    @SuppressWarnings("unchecked")
    private MarketplaceManifest loadManifest(Path dir) throws IOException {
        Path manifestPath = findManifest(dir);
        if (manifestPath == null) throw new IOException("No marketplace.json found in " + dir);

        Map<String, Object> raw = MAPPER.readValue(manifestPath.toFile(), Map.class);
        List<MarketplaceEntry> entries = new ArrayList<>();
        if (raw.get("plugins") instanceof List<?> pluginList) {
            for (Object obj : pluginList) {
                if (obj instanceof Map<?, ?> m) {
                    // source can be string ("./path") or object ({"source":"url","url":"..."})
                    String sourceStr = null;
                    Object sourceObj = m.get("source");
                    if (sourceObj instanceof String s) {
                        sourceStr = s;
                    } else if (sourceObj instanceof Map<?, ?> sm) {
                        // For remote sources, store the git/url for later
                        String type = (String) sm.get("source");
                        if ("url".equals(type)) sourceStr = "git:" + sm.get("url");
                        else if ("git".equals(type)) sourceStr = "git:" + sm.get("url");
                        else if ("git-subdir".equals(type)) sourceStr = "git:" + sm.get("url");
                        else sourceStr = sm.toString();
                    }

                    // author can be string or object
                    String author = null;
                    Object authorObj = m.get("author");
                    if (authorObj instanceof String s) author = s;
                    else if (authorObj instanceof Map<?, ?> am) author = (String) am.get("name");

                    entries.add(new MarketplaceEntry(
                        (String) m.get("id"),
                        (String) m.get("name"),
                        m.get("version") instanceof String v ? v : "latest",
                        (String) m.get("description"),
                        sourceStr,
                        (String) m.get("category"),
                        author
                    ));
                }
            }
        }
        return new MarketplaceManifest(
            (String) raw.get("name"),
            (String) raw.get("description"),
            (String) raw.get("version"),
            entries
        );
    }

    static MarketplaceSource parseSource(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Source cannot be empty");
        }
        input = input.trim();

        // Local path
        if (input.startsWith("/") || input.startsWith("./") || input.startsWith("~")) {
            return new MarketplaceSource.Directory(input);
        }

        // Any URL or owner/repo — just treat as git
        String gitUrl = input;
        if (!input.contains("://") && !input.startsWith("git@")) {
            // owner/repo shorthand → GitHub URL
            gitUrl = "https://github.com/" + input;
            if (!gitUrl.endsWith(".git")) gitUrl += ".git";
        }
        return new MarketplaceSource.Git(gitUrl, "main");
    }

    private static String inferName(String input, MarketplaceSource source) {
        return switch (source) {
            case MarketplaceSource.GitHub g -> g.repo().contains("/")
                ? g.repo().substring(g.repo().indexOf('/') + 1) : g.repo();
            case MarketplaceSource.Git g -> {
                String url = g.url();
                String name = url.substring(url.lastIndexOf('/') + 1);
                if (name.endsWith(".git")) name = name.substring(0, name.length() - 4);
                yield name;
            }
            case MarketplaceSource.Url u -> "url-marketplace";
            case MarketplaceSource.Directory d -> Path.of(d.path()).getFileName().toString();
        };
    }

    private static String sanitize(String name) {
        return name.replaceAll("[^a-zA-Z0-9_-]", "-");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadKnown() {
        try {
            if (Files.isRegularFile(knownFile)) {
                return new HashMap<>(MAPPER.readValue(knownFile.toFile(), Map.class));
            }
        } catch (Exception ignored) {}
        return new HashMap<>();
    }

    private void saveKnown(Map<String, Object> known) {
        try {
            Files.createDirectories(knownFile.getParent());
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(knownFile.toFile(), known);
        } catch (IOException e) {
            log.warn( "Failed to save known_marketplaces.json", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadInstalled() {
        try {
            if (Files.isRegularFile(installedFile)) {
                return new HashMap<>(MAPPER.readValue(installedFile.toFile(), Map.class));
            }
        } catch (Exception ignored) {}
        return new HashMap<>();
    }

    private void saveInstalled(Map<String, Object> installed) {
        try {
            Files.createDirectories(installedFile.getParent());
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(installedFile.toFile(), installed);
        } catch (IOException e) {
            log.warn( "Failed to save installed_plugins.json", e);
        }
    }

    private static void copyRecursive(Path source, Path target) throws IOException {
        Files.createDirectories(target);
        try (var stream = Files.walk(source)) {
            for (var path : stream.toList()) {
                Path dest = target.resolve(source.relativize(path));
                if (Files.isDirectory(path)) {
                    Files.createDirectories(dest);
                } else {
                    Files.copy(path, dest, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static void deleteRecursive(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) return;
        try (var stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                try { Files.delete(p); } catch (IOException ignored) {}
            });
        }
    }
}
