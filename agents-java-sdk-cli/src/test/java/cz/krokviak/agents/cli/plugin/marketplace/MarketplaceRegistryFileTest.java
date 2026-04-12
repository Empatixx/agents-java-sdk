package cz.krokviak.agents.cli.plugin.marketplace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MarketplaceRegistryFileTest {

    @Test
    void missingFileReturnsEmptyMap(@TempDir Path tmp) {
        var store = new MarketplaceRegistryFile(tmp.resolve("does-not-exist.json"));
        assertTrue(store.load().isEmpty());
    }

    @Test
    void roundTripPreservesContent(@TempDir Path tmp) {
        var store = new MarketplaceRegistryFile(tmp.resolve("out.json"));
        Map<String, Object> data = new HashMap<>();
        data.put("alpha", 1);
        data.put("beta", "str");
        store.save(data);

        Map<String, Object> loaded = store.load();
        assertEquals(1, loaded.get("alpha"));
        assertEquals("str", loaded.get("beta"));
    }

    @Test
    void saveCreatesParentDirectories(@TempDir Path tmp) {
        var store = new MarketplaceRegistryFile(tmp.resolve("nested/deep/out.json"));
        store.save(Map.of("k", "v"));
        assertTrue(Files.exists(store.path()));
    }

    @Test
    void corruptFileDegradesToEmpty(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("corrupt.json");
        Files.writeString(file, "not json at all {");
        var store = new MarketplaceRegistryFile(file);
        assertTrue(store.load().isEmpty());
    }

    @Test
    void loadReturnsMutableMap(@TempDir Path tmp) {
        var store = new MarketplaceRegistryFile(tmp.resolve("m.json"));
        store.save(Map.of("k", "v"));
        Map<String, Object> loaded = store.load();
        loaded.put("added-later", true);  // must not throw
        assertTrue(loaded.containsKey("added-later"));
    }
}
