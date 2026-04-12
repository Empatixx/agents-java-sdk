package cz.krokviak.agents.cli.plugin.marketplace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FileOpsTest {

    @Test
    void copyRecursiveMirrorsDirectoryTree(@TempDir Path tmp) throws IOException {
        Path src = tmp.resolve("src");
        Files.createDirectories(src.resolve("sub"));
        Files.writeString(src.resolve("a.txt"), "top");
        Files.writeString(src.resolve("sub/b.txt"), "nested");

        Path dst = tmp.resolve("dst");
        FileOps.copyRecursive(src, dst);

        assertEquals("top", Files.readString(dst.resolve("a.txt")));
        assertEquals("nested", Files.readString(dst.resolve("sub/b.txt")));
    }

    @Test
    void copyRecursiveOverwritesExisting(@TempDir Path tmp) throws IOException {
        Path src = tmp.resolve("src");
        Files.createDirectories(src);
        Files.writeString(src.resolve("x.txt"), "new");

        Path dst = tmp.resolve("dst");
        Files.createDirectories(dst);
        Files.writeString(dst.resolve("x.txt"), "old");

        FileOps.copyRecursive(src, dst);
        assertEquals("new", Files.readString(dst.resolve("x.txt")));
    }

    @Test
    void deleteRecursiveRemovesEntireTree(@TempDir Path tmp) throws IOException {
        Path dir = tmp.resolve("victim");
        Files.createDirectories(dir.resolve("deep/nested"));
        Files.writeString(dir.resolve("deep/nested/leaf.txt"), "x");
        Files.writeString(dir.resolve("top.txt"), "y");

        FileOps.deleteRecursive(dir);
        assertFalse(Files.exists(dir));
    }

    @Test
    void deleteRecursiveNoopOnMissingPath(@TempDir Path tmp) throws IOException {
        FileOps.deleteRecursive(tmp.resolve("does-not-exist"));
        // Must not throw; tmp itself still exists.
        assertTrue(Files.exists(tmp));
    }

    @Test
    void deleteRecursiveNoopOnFile(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("regular.txt");
        Files.writeString(file, "x");
        FileOps.deleteRecursive(file);
        assertTrue(Files.exists(file));
    }
}
