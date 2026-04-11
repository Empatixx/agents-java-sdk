package cz.krokviak.agents.cli.plugin.marketplace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * Git clone/pull operations for marketplace and plugin sources.
 */
public final class GitOps {
    private static final Logger log = LoggerFactory.getLogger(GitOps.class);

    private static final int TIMEOUT_SECONDS = 120;

    private GitOps() {}

    /**
     * Shallow clone a repo. If target exists and is a git repo, pull instead.
     */
    public static void cloneOrPull(String gitUrl, String ref, Path targetDir) throws IOException {
        if (Files.isDirectory(targetDir.resolve(".git"))) {
            pull(targetDir);
        } else {
            clone(gitUrl, ref, targetDir);
        }
    }

    public static void clone(String gitUrl, String ref, Path targetDir) throws IOException {
        Files.createDirectories(targetDir.getParent());

        var args = new java.util.ArrayList<>(java.util.List.of(
            "git", "clone", "--depth", "1", "--recurse-submodules"
        ));
        if (ref != null && !ref.isBlank() && !"main".equals(ref)) {
            args.add("--branch");
            args.add(ref);
        }
        args.add(gitUrl);
        args.add(targetDir.toAbsolutePath().toString());

        exec(args.toArray(String[]::new), null, "Clone failed: " + gitUrl);
    }

    public static void pull(Path repoDir) throws IOException {
        try {
            exec(new String[]{"git", "pull", "origin", "HEAD", "--ff-only"}, repoDir,
                "Pull failed in " + repoDir);
        } catch (IOException e) {
            // If pull fails (diverged, etc.), try fetch + reset
            log.warn( "Pull failed, trying fetch+reset: " + e.getMessage());
            exec(new String[]{"git", "fetch", "origin"}, repoDir, "Fetch failed");
            exec(new String[]{"git", "reset", "--hard", "origin/HEAD"}, repoDir, "Reset failed");
        }
    }

    /**
     * Get the current HEAD SHA.
     */
    public static String headSha(Path repoDir) {
        try {
            ProcessBuilder pb = new ProcessBuilder("git", "rev-parse", "--short", "HEAD");
            pb.directory(repoDir.toFile());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String sha = new String(p.getInputStream().readAllBytes()).trim();
            p.waitFor(10, TimeUnit.SECONDS);
            return p.exitValue() == 0 ? sha : "unknown";
        } catch (Exception e) {
            return "unknown";
        }
    }

    private static void exec(String[] command, Path workDir, String errorMsg) throws IOException {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            if (workDir != null) pb.directory(workDir.toFile());
            pb.redirectErrorStream(true);
            Process process = pb.start();

            String output = new String(process.getInputStream().readAllBytes()).trim();
            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                throw new IOException(errorMsg + " (timed out after " + TIMEOUT_SECONDS + "s)");
            }
            if (process.exitValue() != 0) {
                throw new IOException(errorMsg + ": " + output);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(errorMsg + " (interrupted)", e);
        }
    }
}
