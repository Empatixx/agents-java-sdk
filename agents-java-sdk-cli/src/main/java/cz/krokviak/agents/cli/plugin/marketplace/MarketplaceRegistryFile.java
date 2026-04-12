package cz.krokviak.agents.cli.plugin.marketplace;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Thin JSON-backed persistence for the marketplace registry files
 * ({@code known_marketplaces.json}, {@code installed_plugins.json}).
 *
 * <p>Lifted out of {@code MarketplaceManager} so the 4 near-identical
 * load/save helpers live in one place with a single pretty-printer and
 * error-swallowing policy. Each instance is pinned to one file.
 */
public final class MarketplaceRegistryFile {

    private static final Logger log = LoggerFactory.getLogger(MarketplaceRegistryFile.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path file;

    public MarketplaceRegistryFile(Path file) {
        this.file = file;
    }

    /** @return parsed contents, or an empty mutable map if the file is missing/unreadable */
    @SuppressWarnings("unchecked")
    public Map<String, Object> load() {
        try {
            if (Files.isRegularFile(file)) {
                return new HashMap<>(MAPPER.readValue(file.toFile(), Map.class));
            }
        } catch (Exception e) {
            log.debug("Failed to read {}: {}", file, e.getMessage());
        }
        return new HashMap<>();
    }

    /** Pretty-print {@code data} to the file; creates parent dirs as needed. Errors are logged, not thrown. */
    public void save(Map<String, Object> data) {
        try {
            Files.createDirectories(file.getParent());
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), data);
        } catch (IOException e) {
            log.warn("Failed to save {}: {}", file, e.getMessage());
        }
    }

    /** Path of the backing file, mostly for testing / diagnostics. */
    public Path path() { return file; }
}
