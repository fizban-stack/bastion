package io.xpipe.app.sessions;

import io.xpipe.app.issue.ErrorEventFactory;
import io.xpipe.app.prefs.AppPrefs;
import io.xpipe.app.process.ShellControl;
import io.xpipe.app.storage.DataStorage;
import io.xpipe.core.OsType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Attaches transcription to a {@link ShellControl} for one session.
 *
 * <p>Phase 1 captures session boundaries — start, end, store identity, OS,
 * elapsed time — written to {@code manifest.json} inside the session
 * directory. Per-command stdout / stderr capture is a follow-up (it requires
 * tapping {@code ShellControl.command(...)} call sites, which is a wider
 * change).
 *
 * <p>The class is a no-op unless {@link AppPrefs#sessionTranscription} is
 * true at the moment the shell session opens. Re-evaluating the toggle while
 * a session is live is deliberately not supported — transcription is decided
 * once per session.
 */
public final class ShellSessionTranscriber {

    private ShellSessionTranscriber() {}

    /**
     * Wire transcription lifecycle hooks onto an unstarted {@link
     * ShellControl}. Safe to call before {@code start()}; the hooks fire as
     * the existing init / kill / exit callbacks fire.
     *
     * @return {@code true} if a session manifest will be written for this
     *         shell, {@code false} if the toggle was off (no work was done).
     */
    public static boolean attach(ShellControl sc) {
        if (sc == null || AppPrefs.get() == null) {
            return false;
        }
        var enabled = AppPrefs.get().sessionTranscription.getValue();
        if (!Boolean.TRUE.equals(enabled)) {
            return false;
        }

        var state = new SessionState();

        sc.onInit(c -> {
            try {
                openSession(state, c);
            } catch (Throwable t) {
                ErrorEventFactory.fromThrowable(t).expected().handle();
            }
        });
        sc.onExit(c -> closeSession(state, /*killed*/ false));
        sc.onKill(() -> closeSession(state, /*killed*/ true));

        return true;
    }

    private static void openSession(SessionState state, ShellControl sc) throws IOException {
        var storeId = sc.getSourceStoreId().orElse(null);
        var storeName = resolveStoreName(sc);
        var sessionId = SessionRegistry.newSessionId(
                Instant.now(),
                storeName != null ? storeName : "shell");

        var manifest = SessionManifest.builder()
                .sessionId(sessionId)
                .storeId(storeId != null ? storeId.toString() : null)
                .storeName(storeName)
                .kind("shell")
                .osType(OsType.ofLocal().getName())
                .started(Instant.now())
                .recordingEnabled(false)
                .transcriptionMode(TranscriptionMode.COMMANDS)
                .build();

        var dir = SessionRegistry.createSessionDir(manifest);
        state.dir.set(dir);
        state.manifest.set(manifest);
    }

    private static void closeSession(SessionState state, boolean killed) {
        var dir = state.dir.get();
        var open = state.manifest.get();
        if (dir == null || open == null) {
            return;
        }
        // Idempotent — fired by both onExit and onKill in some failure paths.
        if (!state.closed.compareAndSet(false, true)) {
            return;
        }

        try {
            var finished = open.toBuilder()
                    .ended(Instant.now())
                    .sizeBytes(directorySize(dir))
                    .notes(killed ? "killed" : null)
                    .build();
            SessionRegistry.writeManifest(dir, finished);
            SessionRegistry.appendIndex(finished);
        } catch (Throwable t) {
            ErrorEventFactory.fromThrowable(t).expected().handle();
        }
    }

    private static String resolveStoreName(ShellControl sc) {
        var storeId = sc.getSourceStoreId();
        if (storeId.isEmpty() || DataStorage.get() == null) {
            return Optional.ofNullable(sc.getOsName()).orElse("shell");
        }
        return DataStorage.get()
                .getStoreEntryIfPresent(storeId.get())
                .map(DataStorage.get()::getStoreEntryDisplayName)
                .orElse("shell");
    }

    private static long directorySize(Path dir) {
        try (var stream = Files.walk(dir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .mapToLong(p -> {
                        try {
                            return Files.size(p);
                        } catch (IOException e) {
                            return 0L;
                        }
                    })
                    .sum();
        } catch (IOException e) {
            return 0L;
        }
    }

    private static final class SessionState {
        final AtomicReference<Path> dir = new AtomicReference<>();
        final AtomicReference<SessionManifest> manifest = new AtomicReference<>();
        final java.util.concurrent.atomic.AtomicBoolean closed =
                new java.util.concurrent.atomic.AtomicBoolean(false);
    }
}
