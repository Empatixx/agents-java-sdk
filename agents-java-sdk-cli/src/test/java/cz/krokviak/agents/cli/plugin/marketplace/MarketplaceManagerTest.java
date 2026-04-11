package cz.krokviak.agents.cli.plugin.marketplace;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MarketplaceManagerTest {

    @TempDir
    Path tempDir;
    MarketplaceManager manager;

    @BeforeEach
    void setup() {
        manager = new MarketplaceManager(tempDir);
    }

    @Test
    void parseShorthandBecomesGit() {
        var source = MarketplaceManager.parseSource("owner/repo");
        assertInstanceOf(MarketplaceSource.Git.class, source);
    }

    @Test
    void parseDirectoryPath() {
        var source = MarketplaceManager.parseSource("/home/user/my-plugins");
        assertInstanceOf(MarketplaceSource.Directory.class, source);
        assertEquals("/home/user/my-plugins", ((MarketplaceSource.Directory) source).path());
    }

    @Test
    void addLocalDirectoryMarketplace() throws IOException {
        // Create a local marketplace
        Path mktDir = tempDir.resolve("local-mkt");
        Files.createDirectories(mktDir);
        Files.writeString(mktDir.resolve("marketplace.json"), """
            {
              "name": "local",
              "description": "Local test marketplace",
              "version": "1.0.0",
              "plugins": [
                { "id": "hello", "name": "Hello Plugin", "version": "0.1.0", "description": "Says hello", "source": "./hello" }
              ]
            }
            """);

        // Create the plugin directory
        Path pluginDir = mktDir.resolve("hello");
        Files.createDirectories(pluginDir);
        Files.writeString(pluginDir.resolve("plugin.json"), """
            { "name": "hello", "description": "Says hello" }
            """);

        // Add marketplace
        String name = manager.addMarketplace(mktDir.toAbsolutePath().toString());
        assertEquals("local-mkt", name);

        // Verify known_marketplaces.json created
        assertTrue(Files.isRegularFile(tempDir.resolve("known_marketplaces.json")));

        // Browse plugins
        var available = manager.browsePlugins();
        assertEquals(1, available.size());
        assertEquals("hello", available.getFirst().id());
    }

    @Test
    void installPluginFromLocalMarketplace() throws IOException {
        // Setup marketplace with a plugin
        Path mktDir = tempDir.resolve("test-mkt");
        Files.createDirectories(mktDir);
        Files.writeString(mktDir.resolve("marketplace.json"), """
            {
              "name": "test",
              "version": "1.0.0",
              "plugins": [
                { "id": "greeter", "name": "Greeter", "version": "2.0.0", "description": "Greets", "source": "./greeter" }
              ]
            }
            """);

        Path pluginSrc = mktDir.resolve("greeter");
        Files.createDirectories(pluginSrc.resolve("commands"));
        Files.writeString(pluginSrc.resolve("plugin.json"), """
            { "name": "greeter", "description": "Greets users" }
            """);
        Files.writeString(pluginSrc.resolve("commands").resolve("hi.md"), """
            ---
            name: hi
            description: Say hi
            ---
            Say hi to the user.
            """);

        manager.addMarketplace(mktDir.toAbsolutePath().toString());

        // Install
        var installed = manager.installPlugin("greeter");
        assertEquals("greeter", installed.id());
        assertEquals("2.0.0", installed.version());
        assertTrue(installed.enabled());
        assertTrue(Files.isDirectory(Path.of(installed.installPath())));

        // Verify installed_plugins.json
        assertTrue(Files.isRegularFile(tempDir.resolve("installed_plugins.json")));

        // List installed
        var list = manager.listInstalled();
        assertEquals(1, list.size());
        assertEquals("greeter", list.getFirst().id());
    }

    @Test
    void removePlugin() throws IOException {
        // Setup and install
        Path mktDir = tempDir.resolve("rm-mkt");
        Files.createDirectories(mktDir);
        Files.writeString(mktDir.resolve("marketplace.json"), """
            { "name": "rm", "version": "1.0.0", "plugins": [
              { "id": "removable", "version": "1.0.0", "source": "./removable" }
            ]}
            """);
        Path pluginSrc = mktDir.resolve("removable");
        Files.createDirectories(pluginSrc);
        Files.writeString(pluginSrc.resolve("plugin.json"), """
            { "name": "removable" }
            """);

        manager.addMarketplace(mktDir.toAbsolutePath().toString());
        manager.installPlugin("removable");
        assertEquals(1, manager.listInstalled().size());

        // Remove
        manager.removePlugin("removable");
        assertEquals(0, manager.listInstalled().size());
    }

    @Test
    void enableDisablePlugin() throws IOException {
        Path mktDir = tempDir.resolve("toggle-mkt");
        Files.createDirectories(mktDir);
        Files.writeString(mktDir.resolve("marketplace.json"), """
            { "name": "toggle", "version": "1.0.0", "plugins": [
              { "id": "togglable", "version": "1.0.0", "source": "./togglable" }
            ]}
            """);
        Files.createDirectories(mktDir.resolve("togglable"));
        Files.writeString(mktDir.resolve("togglable").resolve("plugin.json"), """
            { "name": "togglable" }
            """);

        manager.addMarketplace(mktDir.toAbsolutePath().toString());
        manager.installPlugin("togglable");

        assertTrue(manager.listInstalled().getFirst().enabled());

        manager.setEnabled("togglable", false);
        assertFalse(manager.listInstalled().getFirst().enabled());
        assertTrue(manager.enabledPluginPaths().isEmpty());

        manager.setEnabled("togglable", true);
        assertTrue(manager.listInstalled().getFirst().enabled());
        assertEquals(1, manager.enabledPluginPaths().size());
    }

    @Test
    void emptyManagerReturnsEmptyLists() {
        assertTrue(manager.listInstalled().isEmpty());
        assertTrue(manager.browsePlugins().isEmpty());
        assertTrue(manager.listMarketplaces().isEmpty());
        assertTrue(manager.enabledPluginPaths().isEmpty());
    }

    @Test
    void sourceRoundTrip() {
        var gh = new MarketplaceSource.GitHub("owner/repo", "v2");
        var map = gh.toMap();
        var parsed = MarketplaceSource.fromMap(map);
        assertInstanceOf(MarketplaceSource.GitHub.class, parsed);
        assertEquals("owner/repo", ((MarketplaceSource.GitHub) parsed).repo());
        assertEquals("v2", ((MarketplaceSource.GitHub) parsed).ref());
    }

    // ========================= parseSource tests =========================

    @Test
    void parseFullGitHubUrl() {
        var source = MarketplaceManager.parseSource("https://github.com/anthropics/claude-plugins-official.git");
        assertInstanceOf(MarketplaceSource.Git.class, source);
        assertEquals("https://github.com/anthropics/claude-plugins-official.git",
            ((MarketplaceSource.Git) source).url());
    }

    @Test
    void parseOwnerRepoShorthand() {
        var source = MarketplaceManager.parseSource("anthropics/claude-plugins-official");
        assertInstanceOf(MarketplaceSource.Git.class, source);
        assertTrue(((MarketplaceSource.Git) source).url().contains("github.com/anthropics/claude-plugins-official"));
    }

    @Test
    void parseGitlabUrl() {
        var source = MarketplaceManager.parseSource("https://gitlab.com/org/repo.git");
        assertInstanceOf(MarketplaceSource.Git.class, source);
    }

    @Test
    void parseSshUrl() {
        var source = MarketplaceManager.parseSource("git@github.com:owner/repo.git");
        assertInstanceOf(MarketplaceSource.Git.class, source);
    }

    @Test
    void parseLocalPath() {
        var source = MarketplaceManager.parseSource("/home/user/plugins");
        assertInstanceOf(MarketplaceSource.Directory.class, source);
    }

    @Test
    void parseRelativePath() {
        var source = MarketplaceManager.parseSource("./my-plugins");
        assertInstanceOf(MarketplaceSource.Directory.class, source);
    }

    @Test
    void parseTildePath() {
        var source = MarketplaceManager.parseSource("~/plugins");
        assertInstanceOf(MarketplaceSource.Directory.class, source);
    }

    @Test
    void parseTrimsWhitespace() {
        var source = MarketplaceManager.parseSource("  owner/repo  ");
        assertInstanceOf(MarketplaceSource.Git.class, source);
    }

    @Test
    void parseEmptyThrows() {
        assertThrows(IllegalArgumentException.class, () -> MarketplaceManager.parseSource(""));
        assertThrows(IllegalArgumentException.class, () -> MarketplaceManager.parseSource("   "));
        assertThrows(IllegalArgumentException.class, () -> MarketplaceManager.parseSource(null));
    }
}
