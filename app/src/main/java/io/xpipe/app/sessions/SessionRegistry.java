package io.xpipe.app.sessions;

import io.xpipe.app.core.AppProperties;
import io.xpipe.app.issue.ErrorEventFactory;
import io.xpipe.core.JacksonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Filesystem layout and CRUD for session directories under
 * {@code <dataDir>/sessions/}.
 *
 * <pre>
 *   ~/.bastion/sessions/
 *     20260524-014530_my-server-01_a1b2c3/
 *       manifest.json
 *       recording.mp4       (when recording was active)
 *       commands.jsonl      (when transcription mode = COMMANDS)
 *       transcript.cast     (when transcription mode = FULL — future)
 *     _index.jsonl          (one line per finished session, append-only)
 * </pre>
 *
 * <p>Stateless aside from caching the root path on first use. Safe to call
 * from any thread; filesystem operations are inherently the bottleneck.
 */
public final class SessionRegistry {

    private static final DateTimeFormatter DIR_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private SessionRegistry() {}

    /**
     * Root directory for all sessions. Created lazily if missing.
     */
    public static Path root() throws IOException {
        var dir = AppProperties.get().getDataDir().resolve("sessions");
        Files.createDirectories(dir);
        return dir;
    }

    /**
     * Build the canonical session id from a wall-clock instant and a slug.
     * The slug is sanitized (only {@code [A-Za-z0-9-_]} retained) so it is
     * safe as a path component on every supported OS.
     */
    public static String newSessionId(Instant when, String storeSlug) {
        var local = LocalDateTime.ofInstant(when, ZoneId.systemDefault());
        var safeSlug = storeSlug == null
                ? "session"
                : storeSlug.replaceAll("[^A-Za-z0-9._-]", "-");
        if (safeSlug.length() > 60) {
            safeSlug = safeSlug.substring(0, 60);
        }
        return DIR_TIMESTAMP.format(local) + "_" + safeSlug;
    }

    /**
     * Allocate a session directory. Creates the directory and writes an
     * initial manifest.
     */
    public static Path createSessionDir(SessionManifest manifest) throws IOException {
        if (manifest.getSessionId() == null) {
            throw new IllegalArgumentException("SessionManifest.sessionId must be set");
        }
        var dir = root().resolve(manifest.getSessionId());
        Files.createDirectories(dir);
        writeManifest(dir, manifest);
        return dir;
    }

    /**
     * Atomically write {@code manifest.json} inside the given session
     * directory. Uses tmp-file + rename so a crash mid-write cannot leave the
     * manifest truncated.
     */
    public static void writeManifest(Path sessionDir, SessionManifest manifest) throws IOException {
        var tmp = sessionDir.resolve("manifest.json.tmp");
        var dst = sessionDir.resolve("manifest.json");
        var bytes = JacksonMapper.getDefault().writeValueAsBytes(manifest);
        Files.write(tmp, bytes);
        Files.move(tmp, dst, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Read the manifest from a session directory, if present.
     */
    public static Optional<SessionManifest> readManifest(Path sessionDir) {
        var path = sessionDir.resolve("manifest.json");
        if (!Files.isRegularFile(path)) {
            return Optional.empty();
        }
        try {
            return Optional.of(JacksonMapper.getDefault().readValue(path.toFile(), SessionManifest.class));
        } catch (IOException e) {
            ErrorEventFactory.fromThrowable(e).expected().handle();
            return Optional.empty();
        }
    }

    /**
     * Enumerate sessions, newest first. Orphaned directories (no manifest)
     * are skipped silently — callers can prune them via {@link #prune}.
     */
    public static List<SessionManifest> list() {
        var out = new ArrayList<SessionManifest>();
        try (var stream = Files.newDirectoryStream(root(), Files::isDirectory)) {
            for (var dir : stream) {
                readManifest(dir).ifPresent(out::add);
            }
        } catch (IOException e) {
            ErrorEventFactory.fromThrowable(e).expected().handle();
        }
        out.sort(Comparator.comparing(
                SessionManifest::getStarted,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return out;
    }

    /**
     * Append a one-line summary of a finished session to {@code _index.jsonl}.
     * Intended for fast listing without re-reading every manifest.
     */
    public static void appendIndex(SessionManifest manifest) {
        try {
            var line = JacksonMapper.getDefault().writeValueAsString(manifest) + "\n";
            Files.writeString(
                    root().resolve("_index.jsonl"),
                    line,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException e) {
            ErrorEventFactory.fromThrowable(e).expected().handle();
        }
    }

    /**
     * Best-effort cleanup of session directories that lack a manifest. Does
     * NOT honor retention policy — that's a separate concern handled by a
     * future {@code RetentionPolicy} runner.
     */
    public static int prune() {
        int removed = 0;
        try (var stream = Files.newDirectoryStream(root(), Files::isDirectory)) {
            for (var dir : stream) {
                if (!Files.isRegularFile(dir.resolve("manifest.json"))) {
                    deleteRecursive(dir);
                    removed++;
                }
            }
        } catch (IOException e) {
            ErrorEventFactory.fromThrowable(e).expected().handle();
        }
        return removed;
    }

    private static void deleteRecursive(Path dir) throws IOException {
        try (var stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // best effort
                }
            });
        }
    }
}
