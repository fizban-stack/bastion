package io.xpipe.app.sessions;

import io.xpipe.app.issue.ErrorEventFactory;
import io.xpipe.app.issue.TrackEvent;
import io.xpipe.app.prefs.AppPrefs;
import io.xpipe.app.storage.DataStorage;
import io.xpipe.app.util.RemoteDesktopDockEntry;
import io.xpipe.app.util.RemoteDesktopWindow;
import io.xpipe.core.OsType;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Records RDP and VNC sessions as MP4 video by capturing the host's display
 * with ffmpeg. Phase 1 ships the Linux/X11 backend only; Windows (gdigrab),
 * macOS (avfoundation), and Wayland (xdg-desktop-portal / wf-recorder) land in
 * follow-ups.
 *
 * <h2>Design</h2>
 * <p>Bastion can't intercept the RDP or VNC protocol stream — the seven RDP
 * clients and ten VNC clients are external processes that own the connection.
 * The right capture surface is therefore the OS-level display, hooked from
 * {@link RemoteDesktopWindow#onTracked} / {@link RemoteDesktopWindow#onUntracked}
 * so this single class handles every client.
 *
 * <h2>Scope of the X11 prototype</h2>
 * <ul>
 *   <li>Captures the full display ({@code :0.0}) rather than the individual
 *       client window. Per-window capture requires {@code xdotool}-style
 *       PID-to-window mapping, deferred to a follow-up.</li>
 *   <li>No per-store override yet — only the global
 *       {@link AppPrefs#sessionRecording} toggle.</li>
 *   <li>Wayland sessions are detected via {@code XDG_SESSION_TYPE=wayland}
 *       and refused with a single info-level TrackEvent rather than failing
 *       loudly. The portal-based backend will replace this branch.</li>
 *   <li>Requires {@code ffmpeg} on PATH. If absent we log once and stop
 *       trying; recording stays toggleable but inert.</li>
 * </ul>
 */
public final class SessionRecorder {

    private static final Map<RemoteDesktopDockEntry, ActiveRecording> ACTIVE = new ConcurrentHashMap<>();

    private static volatile boolean ffmpegAvailable;
    private static volatile boolean ffmpegProbeDone;

    private SessionRecorder() {}

    /**
     * Register lifecycle hooks against the dock window. Idempotent — calling
     * twice is harmless because the underlying CopyOnWriteArrayList tolerates
     * duplicate listener registrations and the second copy is a no-op against
     * the same {@link #ACTIVE} map.
     *
     * <p>Called from app startup right after
     * {@link RemoteDesktopWindow#init()} in {@code AppBaseMode}.
     */
    public static void init() {
        var window = RemoteDesktopWindow.get();
        if (window == null) {
            return;
        }
        window.onTracked(SessionRecorder::onTracked);
        window.onUntracked(SessionRecorder::onUntracked);
    }

    private static void onTracked(RemoteDesktopDockEntry entry) {
        if (AppPrefs.get() == null || !Boolean.TRUE.equals(AppPrefs.get().sessionRecording.getValue())) {
            return;
        }
        if (OsType.ofLocal() != OsType.LINUX) {
            // Other OS backends not yet implemented.
            return;
        }
        if (isWaylandSession()) {
            TrackEvent.info("Session recording requested on Wayland session — backend not yet available; skipping");
            return;
        }
        if (!isFfmpegAvailable()) {
            return;
        }

        try {
            start(entry);
        } catch (Throwable t) {
            ErrorEventFactory.fromThrowable(t).expected().handle();
        }
    }

    private static void onUntracked(RemoteDesktopDockEntry entry) {
        var rec = ACTIVE.remove(entry);
        if (rec == null) {
            return;
        }
        try {
            rec.stop();
        } catch (Throwable t) {
            ErrorEventFactory.fromThrowable(t).expected().handle();
        }
    }

    private static void start(RemoteDesktopDockEntry entry) throws IOException {
        var storeName = entry.getEntry() != null && DataStorage.get() != null
                ? DataStorage.get().getStoreEntryDisplayName(entry.getEntry())
                : entry.getName();
        var sessionId = SessionRegistry.newSessionId(Instant.now(), storeName);
        var kind = entry.isInternal() ? "vnc" : "rdp";

        var manifest = SessionManifest.builder()
                .sessionId(sessionId)
                .storeId(entry.getEntry() != null ? entry.getEntry().getUuid().toString() : null)
                .storeName(storeName)
                .kind(kind)
                .osType(OsType.ofLocal().getName())
                .started(Instant.now())
                .recordingEnabled(true)
                .transcriptionMode(TranscriptionMode.OFF)
                .build();

        var dir = SessionRegistry.createSessionDir(manifest);
        var output = dir.resolve("recording.mp4");

        var pb = new ProcessBuilder(
                "ffmpeg",
                "-loglevel", "error",
                "-y",
                "-f", "x11grab",
                "-framerate", "15",
                "-i", resolveDisplay(),
                "-c:v", "libx264",
                "-preset", "ultrafast",
                "-pix_fmt", "yuv420p",
                output.toString());
        pb.redirectErrorStream(true);
        pb.redirectOutput(dir.resolve("ffmpeg.log").toFile());

        var process = pb.start();
        ACTIVE.put(entry, new ActiveRecording(process, dir, manifest));
        TrackEvent.withInfo("Session recording started")
                .tag("sessionId", sessionId)
                .tag("kind", kind)
                .tag("output", output.toString())
                .handle();
    }

    private static String resolveDisplay() {
        var d = System.getenv("DISPLAY");
        return d != null && !d.isBlank() ? d : ":0.0";
    }

    private static boolean isWaylandSession() {
        var t = System.getenv("XDG_SESSION_TYPE");
        return t != null && t.equalsIgnoreCase("wayland");
    }

    private static boolean isFfmpegAvailable() {
        if (ffmpegProbeDone) {
            return ffmpegAvailable;
        }
        try {
            var p = new ProcessBuilder("ffmpeg", "-version").redirectErrorStream(true).start();
            if (!p.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)) {
                p.destroyForcibly();
                ffmpegAvailable = false;
            } else {
                ffmpegAvailable = p.exitValue() == 0;
            }
        } catch (Exception e) {
            ffmpegAvailable = false;
        }
        ffmpegProbeDone = true;
        if (!ffmpegAvailable) {
            TrackEvent.warn("ffmpeg not available on PATH — session recording disabled");
        }
        return ffmpegAvailable;
    }

    private static final class ActiveRecording {
        final Process process;
        final Path dir;
        final SessionManifest manifest;
        final Instant started = Instant.now();

        ActiveRecording(Process process, Path dir, SessionManifest manifest) {
            this.process = process;
            this.dir = dir;
            this.manifest = manifest;
        }

        void stop() {
            // ffmpeg finalizes the MP4 cleanly on SIGTERM; process.destroy() sends SIGTERM on Linux.
            if (process.isAlive()) {
                process.destroy();
                try {
                    if (!process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                        process.destroyForcibly();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            var finished = manifest.toBuilder()
                    .ended(Instant.now())
                    .sizeBytes(directorySize(dir))
                    .build();
            try {
                SessionRegistry.writeManifest(dir, finished);
                SessionRegistry.appendIndex(finished);
            } catch (IOException e) {
                ErrorEventFactory.fromThrowable(e).expected().handle();
            }
            TrackEvent.withInfo("Session recording ended")
                    .tag("sessionId", finished.getSessionId())
                    .tag("durationMs", Duration.between(started, Instant.now()).toMillis())
                    .handle();
        }

        private static long directorySize(Path dir) {
            try (var s = java.nio.file.Files.walk(dir)) {
                return s.filter(java.nio.file.Files::isRegularFile)
                        .mapToLong(p -> {
                            try {
                                return java.nio.file.Files.size(p);
                            } catch (IOException e) {
                                return 0L;
                            }
                        })
                        .sum();
            } catch (IOException e) {
                return 0L;
            }
        }
    }
}
