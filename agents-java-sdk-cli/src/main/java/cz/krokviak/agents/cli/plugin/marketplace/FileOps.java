package cz.krokviak.agents.cli.plugin.marketplace;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;

/**
 * Recursive filesystem helpers used by the marketplace installer.
 *
 * <p>Lifted out of {@code MarketplaceManager} so the I/O primitives have
 * their own home, can be unit-tested in isolation (no network / JSON),
 * and can be shared with any future installer that needs the same copy
 * / delete semantics.
 */
public final class FileOps {

    private FileOps() {}

    /** Recursively copy {@code source} tree into {@code target}, replacing existing files. */
    public static void copyRecursive(Path source, Path target) throws IOException {
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

    /** Recursively delete a directory tree. No-op when the path isn't a directory. */
    public static void deleteRecursive(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) return;
        try (var stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                try { Files.delete(p); } catch (IOException ignored) {}
            });
        }
    }
}
