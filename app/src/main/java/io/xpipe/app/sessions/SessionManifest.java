package io.xpipe.app.sessions;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;

/**
 * Per-session metadata persisted as {@code manifest.json} alongside any
 * recording, transcript, or mount-audit artifacts. The manifest is the source
 * of truth for the session — orphaned artifacts without a manifest are pruned
 * on startup by {@link SessionRegistry}.
 *
 * <p>All fields are optional in JSON form so older manifests can be loaded
 * after schema additions. Backward-compatibility comes for free via Jackson
 * default-value semantics.
 */
@Value
@Builder(toBuilder = true)
@Jacksonized
public class SessionManifest {

    /** Stable id of the session directory ({@code yyyyMMdd-HHmmss_<slug>}). */
    String sessionId;

    /** UUID of the {@code DataStoreEntry} this session belongs to, as a String. */
    String storeId;

    /** Display name of the store at session start (snapshot — store may rename later). */
    String storeName;

    /** Session kind: {@code rdp}, {@code vnc}, {@code shell}. */
    String kind;

    /** Local OS at session start ({@code WINDOWS}, {@code MACOS}, {@code LINUX}). */
    String osType;

    /** Wall-clock start. */
    Instant started;

    /** Wall-clock end. Null while in-flight. */
    Instant ended;

    /** True if a screen recording was attempted for this session. */
    Boolean recordingEnabled;

    /** Transcription level in effect when the session opened. */
    TranscriptionMode transcriptionMode;

    /** Total artifact size in bytes (recording + transcript + manifest). Filled at session-end. */
    Long sizeBytes;

    /** Optional free-text notes (reserved for future UI). */
    String notes;
}
